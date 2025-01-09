package com.example.benchmark;

import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class ReactiveDownloadService {

    private final WebClient webClient;

    public ReactiveDownloadService() {
        this.webClient = WebClient.builder().build();
    }

    public Mono<String> callSlowEndpoint(String url) {
        // Non-blocking request, returns immediately with a Mono
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class);
    }
}
