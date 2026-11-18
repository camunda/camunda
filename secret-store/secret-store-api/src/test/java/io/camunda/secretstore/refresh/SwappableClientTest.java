/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.camunda.secretstore.refresh.ClientCheckException.Outcome;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class SwappableClientTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final FakeClient initial = new FakeClient("initial");
  private final TestClient client = new TestClient(initial, TIMEOUT, TIMEOUT);

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void shouldSendNewWorkToTheNewClient() {
    // when
    client.refresh(Map.of("password", "new"));

    // then
    try (var lease = client.lease()) {
      assertThat(lease.client().password).isEqualTo("new");
    }
  }

  @Test
  void shouldCloseTheOldClientOnceItsWorkHasFinished() throws Exception {
    // given
    final var lease = client.lease();

    // when
    client.refresh(Map.of("password", "new"));

    // then
    assertThat(initial.closed).isNotDone();
    lease.close();
    initial.closed.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
  }

  @Test
  void shouldCloseTheOldClientAfterTheDrainTimeout() throws Exception {
    // given
    final var draining = new TestClient(initial, TIMEOUT, Duration.ofMillis(100));
    draining.lease();

    // when
    draining.refresh(Map.of("password", "new"));

    // then
    initial.closed.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    draining.close();
  }

  @Test
  void shouldKeepTheCurrentClientWhenTheCheckFails() {
    // when
    assertThatThrownBy(() -> client.refresh(Map.of("password", "wrong")))
        .isInstanceOf(ClientRefreshException.class)
        .hasRootCauseMessage("rejected");

    // then
    assertThat(client.built.getFirst().closed).isDone();
    assertThat(initial.closed).isNotDone();
    try (var lease = client.lease()) {
      assertThat(lease.client()).isSameAs(initial);
    }
  }

  @Test
  void shouldTellAWrongCredentialFromAnUnreachableService() {
    // when
    final var rejected = catchRefreshFailure("rejected-credentials");
    final var unreachable = catchRefreshFailure("unreachable");
    final var failed = catchRefreshFailure("wrong");

    // then
    assertThat(rejected.outcome()).isEqualTo(Outcome.AUTHENTICATION_FAILED);
    assertThat(unreachable.outcome()).isEqualTo(Outcome.UNREACHABLE);
    assertThat(failed.outcome()).isEqualTo(Outcome.FAILED);
  }

  @Test
  void shouldKeepTheCurrentClientWhenTheBuildTimesOut() throws Exception {
    // given
    final var slow = new TestClient(initial, Duration.ofMillis(100), TIMEOUT);
    slow.blockBuild = new CountDownLatch(1);

    // when
    assertThatThrownBy(() -> slow.refresh(Map.of("password", "new")))
        .isInstanceOf(ClientRefreshException.class)
        .hasMessageContaining("within");

    // then
    slow.blockBuild.countDown();
    slow.lateBuild.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).closed.get(10, TimeUnit.SECONDS);
    try (var lease = slow.lease()) {
      assertThat(lease.client()).isSameAs(initial);
    }
    slow.close();
  }

  private ClientRefreshException catchRefreshFailure(final String password) {
    return catchThrowableOfType(
        ClientRefreshException.class, () -> client.refresh(Map.of("password", password)));
  }

  private static final class FakeClient implements AutoCloseable {

    private final String password;
    private final CompletableFuture<Void> closed = new CompletableFuture<>();

    private FakeClient(final String password) {
      this.password = password;
    }

    @Override
    public void close() {
      closed.complete(null);
    }
  }

  private static final class TestClient extends SwappableClient<FakeClient> {

    private final java.util.List<FakeClient> built =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    private final CompletableFuture<FakeClient> lateBuild = new CompletableFuture<>();
    private volatile CountDownLatch blockBuild = new CountDownLatch(0);

    private TestClient(
        final FakeClient initial, final Duration buildTimeout, final Duration drainTimeout) {
      super("test", Set.of("password"), initial, buildTimeout, drainTimeout);
    }

    @Override
    protected FakeClient build(final Map<String, String> values) throws InterruptedException {
      blockBuild.await();
      final var client = new FakeClient(values.get("password"));
      built.add(client);
      lateBuild.complete(client);
      return client;
    }

    @Override
    protected void check(final FakeClient client) throws ClientCheckException {
      switch (client.password) {
        case "wrong" -> throw new IllegalStateException("rejected");
        case "rejected-credentials" ->
            throw ClientCheckException.authenticationFailed("rejected", null);
        case "unreachable" -> throw ClientCheckException.unreachable("unreachable", null);
        default -> {}
      }
    }
  }
}
