# IDE Feedback Gateway

A Spring Boot service that sits between the IDE plugin (currently the VS Code extension) and
Moodle. Students run their tests in the IDE; the plugin posts the results, and optionally the
test source files, to this gateway. The gateway checks who the student is with Microsoft Graph,
validates the run, and forwards it to the `mod_idetestfeedback` Moodle plugin through Moodle's
web service API.

```
VS Code plugin ──(Bearer: Microsoft Graph token)──▶ gateway ──▶ Microsoft Graph  (who is this?)
                                                       │
                                                       └──(wstoken)──▶ Moodle web service
```

The plugin never sees a Moodle token, and Moodle never sees the student's Microsoft token.

## Requirements

- Java 21
- A Moodle site with `mod_idetestfeedback` installed and a web service token that may call
  `mod_idetestfeedback_submit_test_run`
- The ID of the Microsoft Entra tenant whose users may submit

## Configuration

Defaults live in `src/main/resources/application.yaml`, which is packaged into the jar. Three
settings have no default and must come from the environment:

| Variable          | Property          | Description                                               |
|-------------------|-------------------|-----------------------------------------------------------|
| `GRAPH_TENANT_ID` | `graph.tenant-id` | Entra tenant ID; tokens from any other tenant are refused |
| `MOODLE_BASE_URL` | `moodle.base-url` | Moodle site root, e.g. `https://moodle.example.edu`       |
| `MOODLE_TOKEN`    | `moodle.token`    | Moodle web service token                                  |

The rest have working defaults. To change one, set it as an environment variable (`graph.cache-ttl`
becomes `GRAPH_CACHE_TTL`) or pass it on the command line (`--graph.cache-ttl=10m`):

| Property                         | Default                                | Description                                                     |
|----------------------------------|----------------------------------------|-----------------------------------------------------------------|
| `graph.base-url`                 | `https://graph.microsoft.com/v1.0`     | Microsoft Graph endpoint                                        |
| `graph.cache-ttl`                | `5m`                                   | Longest time a verified token is trusted without asking Graph   |
| `graph.cache-max-size`           | `10000`                                | Maximum number of cached tokens                                 |
| `graph.connect-timeout`          | `3s`                                   |                                                                 |
| `graph.read-timeout`             | `5s`                                   |                                                                 |
| `moodle.submit-run-function`     | `mod_idetestfeedback_submit_test_run`  | Moodle web service function to call                             |
| `moodle.validation-error-prefix` | `validation_`                          | Moodle error codes with this prefix are shown to the student    |
| `moodle.connect-timeout`         | `5s`                                   |                                                                 |
| `moodle.read-timeout`            | `30s`                                  |                                                                 |

The application refuses to start if a required setting is missing or invalid.

## Building

```bash
./mvnw package
```

This runs the tests and writes `target/ide-feedback-gateway-<version>.jar`. On Windows, use
`mvnw.cmd` in place of `./mvnw`.

## Running

```bash
export GRAPH_TENANT_ID=<tenant-id>
export MOODLE_BASE_URL=https://moodle.example.edu
export MOODLE_TOKEN=<token>
java -jar ide-feedback-gateway-0.1.0.jar
```

On Windows PowerShell:

```powershell
$env:GRAPH_TENANT_ID = "<tenant-id>"
$env:MOODLE_BASE_URL = "https://moodle.example.edu"
$env:MOODLE_TOKEN = "<token>"
java -jar ide-feedback-gateway-0.1.0.jar
```

The service listens on port 8080; add `--server.port=<port>` to change it. During development,
`./mvnw spring-boot:run` starts it from source without building a jar.

## Authentication

Every request under `/api/**` needs an `Authorization: Bearer <token>` header carrying a
**Microsoft Graph access token** with the `User.Read` scope. That is what the VS Code plugin gets
from VS Code's built-in Microsoft sign-in. Everything outside `/api/**` is denied.

The gateway treats the token as opaque and checks it by calling Graph with it:

1. `GET /organization`: the token must belong to the tenant set in `GRAPH_TENANT_ID`.
   Personal Microsoft accounts and other tenants are refused.
2. `GET /me`: reads the user's `userPrincipalName` (UPN). Guest accounts (UPNs containing
   `#EXT#`) are refused.

The two calls run in parallel. The UPN is what the gateway sends to Moodle as the student's email,
so it must match the email on the student's Moodle account.

A verified token is cached for `graph.cache-ttl` or until the token's own `exp` claim, whichever
comes first, so Graph is not called on every request. A revoked token can therefore keep working
for up to `graph.cache-ttl`. Refused tokens are never cached.

> **Temporary design.** Accepting plain Graph tokens is a stopgap until the gateway has its own
> app registration in Entra. With one, the plugin would request a token for the gateway's own API
> and the gateway would validate it as a JWT, including its audience.

## API

### `POST /api/v1/idetestfeedback/runs`

Submits one test run for the signed-in student.

```json
{
  "assignmentKey": "assignment-101",
  "ide": "VSCODE",
  "projectName": "my-project",
  "commitHash": "a329d43f8b1e",
  "repoUrl": "https://github.com/org/repo",
  "startedAtMs": 1758134400000,
  "finishedAtMs": 1758134403120,
  "results": [
    {
      "testSuite": "test_calculator",
      "testName": "test_add_returns_sum",
      "status": "FAILED",
      "durationMs": 12,
      "message": "AssertionError: assert 4 == 5",
      "source": { "path": "tests/test_calculator.py", "startLine": 12, "endLine": 13 }
    }
  ],
  "testFiles": [
    { "path": "tests/test_calculator.py", "content": "import pytest\n...", "truncated": false }
  ],
  "captureDisabled": false
}
```

| Field                              | Rules                                                                    |
|------------------------------------|--------------------------------------------------------------------------|
| `assignmentKey`                    | Required, at most 100 characters, only letters, digits, `_` and `-`      |
| `ide`                              | Required, `VSCODE`                                                       |
| `projectName`                      | At most 255 characters                                                   |
| `commitHash`                       | At most 100 characters                                                   |
| `repoUrl`                          | At most 1024 characters                                                  |
| `startedAtMs`, `finishedAtMs`      | Epoch milliseconds, not negative; `finishedAtMs` not before `startedAtMs` |
| `results`                          | Required, 1 to 5000 entries, no `null` entries                           |
| `results[].testSuite`              | At most 255 characters                                                   |
| `results[].testName`               | Required, at most 1024 characters                                        |
| `results[].status`                 | Required: `PASSED`, `FAILED`, `SKIPPED` or `ERROR`                       |
| `results[].durationMs`             | Not negative                                                             |
| `results[].message`                | At most 4000 characters                                                  |
| `results[].source`                 | Optional. `path` at most 1024 characters; `startLine` and `endLine` are both set or both left out, need a `path`, are at least 1, and `endLine` is not before `startLine` |
| `testFiles`                        | At most 200 entries, no `null` entries                                   |
| `testFiles[].path`                 | Required, at most 1024 characters                                        |
| `testFiles[].content`              | All files' content together at most 1,000,000 characters                 |
| `captureDisabled`                  | When `true`, file content is dropped before the run reaches Moodle; only the paths are kept |

Unknown fields are ignored, so the plugin can send more than the gateway uses.

A successful submission returns `201 Created` with the run ID Moodle assigned:

```json
{ "runId": 42, "status": "ok" }
```

### Errors

Every error uses the same body:

```json
{
  "errorMessage": "Validation failed",
  "errorCode": "VALIDATION_ERROR",
  "details": ["results[0].testName: must not be blank"]
}
```

| Status | `errorCode`               | Meaning                                                                         |
|--------|---------------------------|---------------------------------------------------------------------------------|
| 400    | `VALIDATION_ERROR`        | The request broke one of the rules above; `details` names each field            |
| 400    | `MALFORMED_REQUEST`       | The body is not valid JSON, or a value has the wrong type or an unknown enum value |
| 400    | `MOODLE_VALIDATION_ERROR` | Moodle refused the run for a reason meant for the student, e.g. a closed submission window; `errorMessage` is Moodle's message |
| 401    | `UNAUTHORIZED`            | The token is missing, invalid, from another tenant, or belongs to a guest       |
| 502    | `MOODLE_UNAVAILABLE`      | Moodle could not be reached or failed; `details` holds Moodle's error code when there is one |
| 503    | `AUTH_UNAVAILABLE`        | Microsoft Graph could not be reached, is throttling the gateway, or refused the gateway's lookup |

For `MOODLE_UNAVAILABLE`, the client gets a fixed message. Moodle's full response is only written
to the gateway's log.

## How runs reach Moodle

The gateway calls Moodle's REST endpoint, `/webservice/rest/server.php`, with a form-encoded POST:

- `wstoken`, `wsfunction` and `moodlewsrestformat=json`
- the run's scalar fields as separate parameters (`email`, `assignmentkey`, `ide`, `projectname`,
  `commithash`, `repourl`, `startedatms`, `finishedatms`, `capturedisabled`); fields the plugin
  left out are not sent, and booleans are sent as `1`/`0`
- the `results` and `testFiles` lists as a single JSON string in `payload`, with all keys in
  lowercase (`testfiles`, `testname`, `durationms`, ...)

When Moodle answers with an error, an error code starting with `moodle.validation-error-prefix`
becomes `MOODLE_VALIDATION_ERROR`; anything else becomes `MOODLE_UNAVAILABLE`.

## Logging and privacy

Requests to the gateway and to Moodle are logged at INFO with method, URL, status, duration and
response body; responses with status 400 or above are logged at WARN. Request bodies contain
student source code and email addresses, so they are only logged at DEBUG.
Leave DEBUG off in production: at that level, bodies are logged even for students who set
`captureDisabled`. The Moodle token is always masked in logs.

## Testing

```bash
./mvnw test
```

The tests need no network access: Graph and Moodle are replaced by mock servers.

## Project layout

```
src/main/java/com/example/idefeedbackgateway/
├── api/                     REST controller, request/response DTOs, error handling, request logging
├── service/                 Turns an API request into a Moodle payload (applies captureDisabled)
├── integration/moodle/      Moodle web service client, its configuration and DTOs
├── security/                Graph token introspection, security filter chains, auth error responses
├── logging/                 Shared HTTP logging helpers
└── model/                   Enums shared by the API and Moodle layers
```
