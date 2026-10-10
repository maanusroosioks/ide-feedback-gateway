package com.example.idefeedbackgateway.integration.moodle.config;

import com.example.idefeedbackgateway.logging.HttpExchangeLogSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

@Slf4j
public class MoodleLoggingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String method = request.getMethod().toString();
        String uri = request.getURI().toString();
        HttpExchangeLogSupport.logRequest(log, "MOODLE", method, uri, redactToken(HttpExchangeLogSupport.decode(body)));

        long startedAt = System.currentTimeMillis();
        ClientHttpResponse response;
        try {
            response = execution.execute(request, body);
        } catch (IOException ex) {
            HttpExchangeLogSupport.logTransportFailure(log, "MOODLE", method, uri, ex);
            throw ex;
        }

        HttpExchangeLogSupport.logResponse(log, "MOODLE", method, uri, response.getStatusCode().value(),
                System.currentTimeMillis() - startedAt, HttpExchangeLogSupport.decode(response.getBody().readAllBytes()));
        return response;
    }

    private String redactToken(String formBody) {
        return formBody.replaceAll("wstoken=[^&]*", "wstoken=***");
    }
}
