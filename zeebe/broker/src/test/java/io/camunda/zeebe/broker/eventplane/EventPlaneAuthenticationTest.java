/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.camunda.zeebe.broker.system.configuration.EventPlaneCfg;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class EventPlaneAuthenticationTest {

  private final List<String> forms = new CopyOnWriteArrayList<>();
  private final AtomicInteger issued = new AtomicInteger();
  private final AtomicReference<Instant> now =
      new AtomicReference<>(Instant.parse("2026-10-16T08:00:00Z"));
  private final EventPlaneCfg cfg = new EventPlaneCfg();
  private volatile CountDownLatch answer = new CountDownLatch(0);
  private HttpServer server;
  private HttpClient http;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/token", this::issue);
    server.start();
    http = HttpClient.newHttpClient();
  }

  @AfterEach
  void stop() {
    http.shutdownNow();
    server.stop(0);
  }

  @Test
  void needsNothingWithoutCredentials() {
    // when
    final var authorization = authentication().authorization().join();

    // then
    assertThat(authorization).isEmpty();
  }

  @Test
  void authenticatesWithAUsername() {
    // given
    cfg.setUsername("demo");
    cfg.setPassword("secret");

    // when
    final var authorization = authentication().authorization().join();

    // then
    assertThat(authorization)
        .contains(
            "Basic "
                + Base64.getEncoder()
                    .encodeToString("demo:secret".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void asksForTheTokenItIsConfiguredFor() {
    // given
    useClient();
    cfg.setAudience("event-plane");
    cfg.setScope("api://event-plane/.default");
    cfg.setResource("https://event-plane.example.com");

    // when
    final var authorization = authentication().authorization().join();

    // then
    assertThat(authorization).contains("Bearer t1");
    assertThat(forms)
        .singleElement()
        .asString()
        .contains(
            "grant_type=client_credentials",
            "client_id=my-client",
            "client_secret=secret",
            "audience=event-plane",
            "scope=api%3A%2F%2Fevent-plane%2F.default",
            "resource=https%3A%2F%2Fevent-plane.example.com");
  }

  @Test
  void leavesOutWhatIsNotConfigured() {
    // given
    useClient();

    // when
    authentication().authorization().join();

    // then
    assertThat(forms).singleElement().asString().doesNotContain("audience", "scope", "resource");
  }

  @Test
  void keepsTheTokenUntilShortlyBeforeItExpires() {
    // given
    useClient();
    final var authentication = authentication();
    authentication.authorization().join();

    // when
    now.set(now.get().plusSeconds(300 - 31));
    final var beforeExpiry = authentication.authorization().join();
    now.set(now.get().plusSeconds(2));
    final var nearExpiry = authentication.authorization().join();

    // then
    assertThat(beforeExpiry).contains("Bearer t1");
    assertThat(nearExpiry).contains("Bearer t2");
  }

  @Test
  void fetchesOneTokenForCallersAtTheSameTime() throws Exception {
    // given
    useClient();
    answer = new CountDownLatch(1);
    final var authentication = authentication();

    // when
    final var first = authentication.authorization();
    final var second = authentication.authorization();
    answer.countDown();

    // then
    assertThat(first.get(10, TimeUnit.SECONDS)).contains("Bearer t1");
    assertThat(second.get(10, TimeUnit.SECONDS)).contains("Bearer t1");
    assertThat(forms).hasSize(1);
  }

  @Test
  void dropsOnlyTheTokenThatWasRefused() {
    // given
    useClient();
    final var authentication = authentication();
    final var old = authentication.authorization().join();
    authentication.refused(old);
    final var current = authentication.authorization().join();

    // when
    authentication.refused(old);

    // then
    assertThat(current).contains("Bearer t2");
    assertThat(authentication.authorization().join()).isEqualTo(current);
  }

  @Test
  void failsWithoutATokenUrl() {
    // given
    useClient();
    cfg.setTokenUrl(null);

    // when
    final var authorization = authentication().authorization();

    // then
    Assertions.assertThatThrownBy(authorization::join)
        .isInstanceOf(CompletionException.class)
        .hasMessageContaining("camunda.event-plane.token-url");
  }

  @Test
  void failsWhenTheIdentityProviderRefuses() {
    // given
    useClient();
    cfg.setClientSecret("wrong");

    // when
    final var authorization = authentication().authorization();

    // then
    Assertions.assertThatThrownBy(authorization::join)
        .hasMessageContaining("HTTP 401")
        .hasMessageContaining("invalid_client");
  }

  private EventPlaneAuthentication authentication() {
    return new EventPlaneAuthentication(cfg, http, now::get);
  }

  private void useClient() {
    cfg.setClientId("my-client");
    cfg.setClientSecret("secret");
    cfg.setTokenUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/token"));
  }

  private void issue(final HttpExchange exchange) throws IOException {
    final var form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    forms.add(form);
    try {
      answer.await(10, TimeUnit.SECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    final int status;
    final String body;
    if (form.contains("client_secret=secret")) {
      status = 200;
      body = "{\"access_token\": \"t" + issued.incrementAndGet() + "\", \"expires_in\": 300}";
    } else {
      status = 401;
      body = "{\"error\": \"invalid_client\"}";
    }
    final var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    try (final var out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}
