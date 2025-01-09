package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.web.reactive.config.EnableWebFlux;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
@RestController
@EnableWebFlux
public class SlowServerApplication {
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    public static void main(String[] args) {
        SpringApplication.run(SlowServerApplication.class, args);
    }

    @Bean
    public NettyReactiveWebServerFactory nettyReactiveWebServerFactory() {
        NettyReactiveWebServerFactory factory = new NettyReactiveWebServerFactory();
        factory.addServerCustomizers(builder ->
                builder.option(ChannelOption.SO_BACKLOG, 100)
                        .childOption(ChannelOption.TCP_NODELAY, true));
        return factory;
    }

    @GetMapping("/slow")
    public Mono<String> slowEndpoint() {
        int current = activeRequests.incrementAndGet();
        System.out.printf("Request started (Active: %d)%n", current);

        return Mono.delay(Duration.ofSeconds(2))
                .map(l -> "Response-" + System.currentTimeMillis())
                .doOnSuccess(r -> {
                    int remaining = activeRequests.decrementAndGet();
                    System.out.printf("Request completed (Active: %d)%n", remaining);
                })
                .doOnError(e -> {
                    activeRequests.decrementAndGet();
                    System.out.printf("Request failed: %s%n", e.getMessage());
                });
    }
}