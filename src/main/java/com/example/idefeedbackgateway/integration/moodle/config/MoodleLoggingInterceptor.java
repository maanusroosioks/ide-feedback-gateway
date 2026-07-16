package com.example.idefeedbackgateway.integration.moodle.config;

import com.example.idefeedbackgateway.logging.HttpExchangeLogSupport;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class MoodleLoggingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String method = request.getMethod().toString();
        String uri = request.getURI().toString();
        String requestBody = HttpExchangeLogSupport.truncate(redactToken(HttpExchangeLogSupport.decode(body)));

        long startedAt = System.currentTimeMillis();
        ClientHttpResponse response;
        try {
            response = execution.execute(request, body);
        } catch (IOException ex) {
            HttpExchangeLogSupport.logTransportFailure(log, method, uri, requestBody, ex);
            throw ex;
        }

        byte[] responseBytes = response.getBody().readAllBytes();
        String responseBody = HttpExchangeLogSupport.truncate(HttpExchangeLogSupport.decode(responseBytes));
        HttpExchangeLogSupport.logExchange(log, method, uri, response.getStatusCode().value(),
                System.currentTimeMillis() - startedAt, requestBody, responseBody);

        return new BufferedClientHttpResponse(response, responseBytes);
    }

    private String redactToken(String formBody) {
        return formBody.replaceAll("wstoken=[^&]*", "wstoken=***");
    }

    private record BufferedClientHttpResponse(ClientHttpResponse delegate, byte[] body) implements ClientHttpResponse {

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
