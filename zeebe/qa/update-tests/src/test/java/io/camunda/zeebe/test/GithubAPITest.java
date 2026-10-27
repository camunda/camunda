/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.camunda.zeebe.test.VersionCompatibilityMatrix.GithubAPI;
import io.camunda.zeebe.util.SemanticVersion;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class GithubAPITest {

  private static final int MAX_ATTEMPTS = 3;
  private static final SemanticVersion VERSION = SemanticVersion.parse("8.1.20").orElseThrow();

  private final Queue<Integer> statusCodes = new ArrayDeque<>();
  private final Map<String, String> errorHeaders = new HashMap<>();
  private final AtomicInteger requestCount = new AtomicInteger();
  private HttpServer server;
  private GithubAPI api;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          requestCount.incrementAndGet();
          final var status = statusCodes.remove();
          final var body =
              (status == 200 ? "{\"tag_name\":\"8.1.20\"}" : "{\"message\":\"Server Error\"}")
                  .getBytes(StandardCharsets.UTF_8);
          if (status != 200) {
            errorHeaders.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
          }
          exchange.sendResponseHeaders(status, body.length);
          try (final var out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    server.start();

    final var retry =
        Retry.of(
            "github-api-test",
            RetryConfig.custom()
                .maxAttempts(MAX_ATTEMPTS)
                .waitDuration(Duration.ofMillis(1))
                .build());
    api = new GithubAPI("http://localhost:" + server.getAddress().getPort(), retry);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void shouldRetryReleaseLookupOnServerErrors() {
    // given
    statusCodes.addAll(List.of(502, 502, 200));

    // when
    final var release = api.fetchRelease(VERSION);

    // then
    assertThat(release).isPresent();
    assertThat(release.get().toSemanticVersion()).isEqualTo(VERSION);
    assertThat(requestCount).hasValue(3);
  }

  @Test
  void shouldRetryReleaseLookupWhenRateLimited() {
    // given
    statusCodes.addAll(List.of(429, 200));

    // when
    final var release = api.fetchRelease(VERSION);

    // then
    assertThat(release).isPresent();
    assertThat(requestCount).hasValue(2);
  }

  @Test
  void shouldFailReleaseLookupWhenServerErrorsPersist() {
    // given
    statusCodes.addAll(List.of(502, 502, 502, 200));

    // when / then
    assertThatThrownBy(() -> api.fetchRelease(VERSION))
        .isInstanceOf(RuntimeException.class)
        .hasRootCauseInstanceOf(IOException.class)
        .rootCause()
        .hasMessageContaining("Status: 502")
        .hasMessageContaining("/releases/tags/8.1.20");
    assertThat(requestCount).hasValue(MAX_ATTEMPTS);
  }

  @Test
  void shouldReturnEmptyReleaseWithoutRetryWhenNotFound() {
    // given
    statusCodes.add(404);

    // when
    final var release = api.fetchRelease(VERSION);

    // then
    assertThat(release).isEmpty();
    assertThat(requestCount).hasValue(1);
  }

  @Test
  void shouldRetryReleaseLookupWhenForbiddenByExhaustedRateLimit() {
    // given
    errorHeaders.put("X-RateLimit-Remaining", "0");
    statusCodes.addAll(List.of(403, 200));

    // when
    final var release = api.fetchRelease(VERSION);

    // then
    assertThat(release).isPresent();
    assertThat(requestCount).hasValue(2);
  }

  @Test
  void shouldRetryReleaseLookupWhenForbiddenWithRetryAfter() {
    // given
    errorHeaders.put("Retry-After", "1");
    statusCodes.addAll(List.of(403, 200));

    // when
    final var release = api.fetchRelease(VERSION);

    // then
    assertThat(release).isPresent();
    assertThat(requestCount).hasValue(2);
  }

  @Test
  void shouldNotRetryReleaseLookupWhenForbiddenWithRateLimitRemaining() {
    // given
    errorHeaders.put("X-RateLimit-Remaining", "42");
    statusCodes.addAll(List.of(403, 200));

    // when / then
    assertThatThrownBy(() -> api.fetchRelease(VERSION))
        .isInstanceOf(RuntimeException.class)
        .hasRootCauseMessage(
            "Failed to fetch release from GitHub API. Status: 403, Endpoint: "
                + "http://localhost:"
                + server.getAddress().getPort()
                + "/repos/camunda/camunda/releases/tags/8.1.20, Response: "
                + "{\"message\":\"Server Error\"}");
    assertThat(requestCount).hasValue(1);
  }

  @Test
  void shouldNotRetryReleaseLookupOnPlainForbidden() {
    // given
    statusCodes.addAll(List.of(403, 200));

    // when / then
    assertThatThrownBy(() -> api.fetchRelease(VERSION))
        .isInstanceOf(RuntimeException.class)
        .hasRootCauseMessage(
            "Failed to fetch release from GitHub API. Status: 403, Endpoint: "
                + "http://localhost:"
                + server.getAddress().getPort()
                + "/repos/camunda/camunda/releases/tags/8.1.20, Response: "
                + "{\"message\":\"Server Error\"}");
    assertThat(requestCount).hasValue(1);
  }
}
