package com.tboostai_batch.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

import static com.tboostai_batch.common.GeneralConstants.TIMEOUT_60_SECONDS;
import static com.tboostai_batch.common.GeneralConstants.WEBCLIENT_BUFFER_SIZE;

@Component
public class WebClientUtils {

    private final WebClient.Builder externalWebClientBuilder;
    private final WebClient.Builder internalWebClientBuilder;
    private static final Logger logger = LoggerFactory.getLogger(WebClientUtils.class);

    public WebClientUtils(@Qualifier("createExternalWebClientBuilder") WebClient.Builder webClientBuilder,
                          @Qualifier("createInternalWebClientBuilder") WebClient.Builder internalWebClientBuilder) {
        this.externalWebClientBuilder = webClientBuilder;
        this.internalWebClientBuilder = internalWebClientBuilder;
    }

    public <T> Mono<T> sendGetRequestExternal(String uri, Class<T> responseType, HttpHeaders headers, Integer maxAttempts, Integer durationInSecond) {
        if (uri == null || uri.isEmpty()) {
            logger.error("uri is null or empty");
            return null;
        }
        Mono<T> monoResult = externalWebClientBuilder
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(WEBCLIENT_BUFFER_SIZE))
                .build().get()
                .uri(uri)
                .headers(httpHeaders -> httpHeaders.addAll(headers)) // 添加传入的headers
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                    logger.error("4xx error occurred while requesting {}: {}", uri, clientResponse.statusCode());
                    return Mono.empty();
                })
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                    logger.error("5xx error occurred while requesting {}: {}", uri, clientResponse.statusCode());
                    return Mono.empty();
                })
                .bodyToMono(responseType);

        // 根据参数是否启用 retry 机制
        if (maxAttempts != null && durationInSecond != null && maxAttempts > 0 && durationInSecond > 0) {
            monoResult = monoResult.retryWhen(Retry.fixedDelay(maxAttempts, Duration.ofSeconds(durationInSecond))
                    .doBeforeRetry(retrySignal -> logger.info("Retrying {} due to: {}", uri, retrySignal.failure().getMessage())));
        }

        // 错误处理和日志
        return monoResult
                .doOnError(e -> logger.error("Error occurred while calling {}: {}", uri, e.getMessage(), e))
                .onErrorResume(e -> {
                    logger.error("Handling error for {}: {}", uri, e.getMessage(), e);
                    return Mono.empty();
                });
    }



    public <T> Mono<T> sendExternalPostRequest(String uri, String bodyValue, Map<String, String> headerToValue, Class<T> responseType, Integer maxAttempts, Integer durationInSecond) {

        WebClient.RequestBodySpec uriSpec = externalWebClientBuilder.build().post()
                .uri(uri);

        // Add headers
        for (Map.Entry<String, String> headerEntry : headerToValue.entrySet()) {
            uriSpec.header(headerEntry.getKey(), headerEntry.getValue());
        }

        // Make the request
        Mono<T> monoResult = uriSpec.bodyValue(bodyValue)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                    logger.error("4xx error occurred while requesting {}: {}", uri, clientResponse.statusCode());
                    return Mono.empty();
                })
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                    logger.error("5xx error occurred while requesting {}: {}", uri, clientResponse.statusCode());
                    return Mono.empty();
                })
                .bodyToMono(responseType);

        // Retry logic if provided
        if (maxAttempts != null && durationInSecond != null) {
            monoResult = monoResult.retryWhen(Retry.fixedDelay(maxAttempts, Duration.ofSeconds(durationInSecond))
                    .doBeforeRetry(retrySignal -> logger.info("Retrying due to: {}", retrySignal.failure().getMessage())));
        }

        // Error logging and fallback
        return monoResult
                .doOnError(e -> logger.error("Error occurred while calling {}: {}", uri, e.getMessage(), e))  // Ensure all exceptions are logged
                .onErrorResume(e -> {
                    logger.error("Handling error for {}: {}", uri, e.getMessage(), e);
                    return Mono.empty();
                });
    }

    public <T> Mono<T> sendPostRequestInternal(String uri, Object bodyValue, Class<T> responseType) {
        return internalWebClientBuilder.build().post()
                .uri(uri)
                .bodyValue(bodyValue)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                        Mono.error(new RuntimeException("Client error occurred while requesting " + uri))
                )
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                        Mono.error(new RuntimeException("Server error occurred while requesting " + uri))
                )
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(TIMEOUT_60_SECONDS))
                .doOnError(WebClientResponseException.class, e -> logger.error("Error response: {}", e.getResponseBodyAsString()))
                .onErrorResume(e -> {
                    if (e instanceof io.netty.handler.timeout.ReadTimeoutException) {
                        logger.error("Timeout error while calling {}: {}", uri, e.getMessage());
                        return Mono.empty();
                    }
                    logger.error("Error occurred while requesting {}: {}", uri, e.getMessage(), e);
                    return Mono.empty();
                });
    }

    public <T> Mono<T> sendGetRequestExternal(String uri, Class<T> responseType) {
        return externalWebClientBuilder.build().get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                        Mono.error(new RuntimeException("Client error occurred while requesting " + uri))
                )
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                        Mono.error(new RuntimeException("Server error occurred while requesting " + uri))
                )
                .bodyToMono(responseType)
                .doOnError(WebClientResponseException.class, e -> logger.error("Error response: {}", e.getResponseBodyAsString()))
                .onErrorResume(e -> {
                    logger.error("Failed to retrieve data from {}", uri);
                    return Mono.empty();
                });
    }
}