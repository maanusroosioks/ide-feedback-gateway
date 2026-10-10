package com.example.idefeedbackgateway.integration.moodle.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MoodleProperties.class)
public class MoodleRestClientConfig {

    @Bean
    public RestClient moodleRestClient(RestClient.Builder builder, MoodleProperties properties) {
        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory(properties))
                .bufferContent((uri, method) -> true)
                .requestInterceptor(new MoodleLoggingInterceptor())
                .build();
    }

    private SimpleClientHttpRequestFactory requestFactory(MoodleProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
