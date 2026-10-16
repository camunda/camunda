/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.system.configuration.EventPlaneCfg;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.RetryDelayStrategy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class EventPlaneRegistrationTest {

  private static final PartitionId PARTITION = new PartitionId("tenanta", 1);
  private static final RetryDelayStrategy NO_DELAY =
      new RetryDelayStrategy() {
        @Override
        public Duration nextDelay() {
          return Duration.ofMillis(10);
        }

        @Override
        public void reset() {}
      };

  private final List<Request> requests = new CopyOnWriteArrayList<>();
  private final Queue<Integer> statuses = new ArrayDeque<>();
  private final Queue<String> tokens = new ArrayDeque<>();
  private HttpServer server;
  private ActorScheduler scheduler;
  private EventPlaneCfg cfg;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::answer);
    server.start();
    scheduler = ActorScheduler.newActorScheduler().build();
    scheduler.start();
    cfg = new EventPlaneCfg();
    cfg.setEndpoint(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
  }

  @AfterEach
  void stop() throws Exception {
    scheduler.close();
    server.stop(0);
  }

  @Test
  void registersTheTenantWithoutAStatus() {
    // when
    register(() -> Optional.of("my cluster"));

    // then
    final var request = awaitRequests(1).getFirst();
    assertThat(request.method()).isEqualTo("PUT");
    assertThat(request.path()).isEqualTo("/v1/environments/my%20cluster/tenanta");
    assertThat(request.contentType()).isEqualTo("application/json");
    assertThat(request.body()).isEqualTo("{}");
    assertThat(request.authorization()).isNull();
  }

  @Test
  void retriesUntilTheEventPlaneAnswers() {
    // given
    statuses.add(503);
    statuses.add(500);

    // when
    register(() -> Optional.of("c"));

    // then
    awaitRequests(3);
    awaitQuiet();
    assertThat(requests).hasSize(3);
  }

  @Test
  void waitsUntilTheClusterIdIsKnown() {
    // given
    final var clusterId = new AtomicReference<Optional<String>>(Optional.empty());
    register(clusterId::get);
    awaitQuiet();
    assertThat(requests).isEmpty();

    // when
    clusterId.set(Optional.of("c"));

    // then
    assertThat(awaitRequests(1).getFirst().path()).isEqualTo("/v1/environments/c/tenanta");
  }

  @Test
  void authenticatesWithAUsername() {
    // given
    cfg.setUsername("demo");
    cfg.setPassword("secret");

    // when
    register(() -> Optional.of("c"));

    // then
    assertThat(awaitRequests(1).getFirst().authorization())
        .isEqualTo(
            "Basic "
                + Base64.getEncoder()
                    .encodeToString("demo:secret".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void authenticatesWithAClientsToken() {
    // given
    useClient();
    tokens.add("t1");

    // when
    register(() -> Optional.of("c"));

    // then
    final var sent = awaitRequests(2);
    assertThat(sent.getFirst().path()).isEqualTo("/token");
    assertThat(sent.getFirst().body())
        .contains("grant_type=client_credentials", "client_id=my-client", "audience=event-plane");
    assertThat(sent.get(1).authorization()).isEqualTo("Bearer t1");
  }

  @Test
  void getsANewTokenWhenTheEventPlaneRefusesTheOld() {
    // given
    useClient();
    tokens.add("t1");
    tokens.add("t2");
    statuses.add(401);

    // when
    register(() -> Optional.of("c"));

    // then
    final var sent = awaitRequests(4);
    assertThat(sent)
        .extracting(Request::path)
        .containsExactly(
            "/token", "/v1/environments/c/tenanta", "/token", "/v1/environments/c/tenanta");
    assertThat(sent.get(3).authorization()).isEqualTo("Bearer t2");
  }

  private void useClient() {
    cfg.setClientId("my-client");
    cfg.setClientSecret("secret");
    cfg.setAudience("event-plane");
    cfg.setTokenUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/token"));
  }

  private void register(final Supplier<Optional<String>> clusterId) {
    scheduler.submitActor(new EventPlaneRegistration(cfg, PARTITION, clusterId, NO_DELAY)).join();
  }

  private List<Request> awaitRequests(final int count) {
    await().atMost(Duration.ofSeconds(10)).until(() -> requests.size() >= count);
    return List.copyOf(requests);
  }

  /** Long enough for several retries, had there been any. */
  private void awaitQuiet() {
    final var before = requests.size();
    await().pollDelay(Duration.ofMillis(200)).until(() -> true);
    assertThat(requests).hasSize(before);
  }

  private synchronized void answer(final HttpExchange exchange) throws IOException {
    final var request =
        new Request(
            exchange.getRequestMethod(),
            exchange.getRequestURI().getRawPath(),
            exchange.getRequestHeaders().getFirst("Content-Type"),
            exchange.getRequestHeaders().getFirst("Authorization"),
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    requests.add(request);
    final int status;
    final String body;
    if (request.path().equals("/token")) {
      status = 200;
      body = "{\"access_token\": \"" + tokens.remove() + "\", \"expires_in\": 300}";
    } else {
      status = statuses.isEmpty() ? 201 : statuses.remove();
      body = "{}";
    }
    final var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (final var out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  private record Request(
      String method, String path, String contentType, String authorization, String body) {}
}
