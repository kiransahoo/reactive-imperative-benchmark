package com.example.benchmark;

import org.springframework.web.client.RestTemplate;

public class BlockingDownloadService {

    private final RestTemplate restTemplate = new RestTemplate();

    public String callSlowEndpoint(String url) {
        // Blocks this thread ~2s while the slow server responds
        return restTemplate.getForObject(url, String.class);
    }
}
