/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.mailbox.api.GatewayId;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.OutboxResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

final class ResponseAwaiterTest {

  private static final GatewayId GATEWAY = new GatewayId("gateway-1");
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final MutableInstantSource clock = new MutableInstantSource();
  private final MailboxStore store = new InMemoryMailboxStore(clock);
  private final ResponseAwaiter awaiter = new ResponseAwaiter(store, GATEWAY, clock);

  @Test
  void shouldCompleteTheRequestWithItsResponse() throws Exception {
    // given
    final var future = awaiter.await(1, TIMEOUT);
    store.putResponse(new OutboxResponse(GATEWAY, 1, new byte[] {42}));

    // when
    final var delivered = awaiter.pollOnce();

    // then
    assertThat(delivered).isEqualTo(1);
    assertThat(future.get()).isEqualTo(new byte[] {42});
    assertThat(awaiter.pendingCount()).isZero();
  }

  @Test
  void shouldKeepWaitingWhileThereIsNoResponse() {
    // given
    final var future = awaiter.await(1, TIMEOUT);

    // when
    final var delivered = awaiter.pollOnce();

    // then
    assertThat(delivered).isZero();
    assertThat(future).isNotDone();
    assertThat(awaiter.pendingCount()).isEqualTo(1);
  }

  @Test
  void shouldFailTheRequestWithATimeoutWhenTheTimeIsUp() {
    // given
    final var future = awaiter.await(1, TIMEOUT);

    // when
    clock.advance(TIMEOUT.plusSeconds(1));
    awaiter.pollOnce();

    // then
    assertThatThrownBy(future::get)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(TimeoutException.class);
    assertThat(awaiter.pendingCount()).isZero();
  }

  @Test
  void shouldPreferAResponseThatArrivedBeforeTheTimeoutWasNoticed() throws Exception {
    // given
    final var future = awaiter.await(1, TIMEOUT);
    store.putResponse(new OutboxResponse(GATEWAY, 1, new byte[] {7}));
    clock.advance(TIMEOUT.plusSeconds(1));

    // when
    awaiter.pollOnce();

    // then
    assertThat(future.get()).isEqualTo(new byte[] {7});
  }

  @Test
  void shouldIgnoreResponsesOfAnotherGateway() {
    // given
    final var future = awaiter.await(1, TIMEOUT);
    store.putResponse(new OutboxResponse(new GatewayId("gateway-2"), 1, new byte[] {1}));

    // when
    awaiter.pollOnce();

    // then
    assertThat(future).isNotDone();
  }

  @Test
  void shouldKeepWaitingWhenTheStoreFailsAndStillTimeOut() {
    // given
    final var failingStore = StoreProxy.wrap(store, Set.of("takeResponses"), new ArrayList<>());
    final var failingAwaiter = new ResponseAwaiter(failingStore, GATEWAY, clock);
    final var future = failingAwaiter.await(1, TIMEOUT);

    // when
    final var delivered = failingAwaiter.pollOnce();
    clock.advance(TIMEOUT.plusSeconds(1));
    failingAwaiter.pollOnce();

    // then
    assertThat(delivered).isZero();
    assertThat(future).isCompletedExceptionally();
  }

  @Test
  void shouldRejectASecondWaitForTheSameRequest() {
    // given
    awaiter.await(1, TIMEOUT);

    // when / then
    assertThatThrownBy(() -> awaiter.await(1, TIMEOUT)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldForgetACancelledRequest() {
    // given
    final var future = awaiter.await(1, TIMEOUT);

    // when
    future.cancel(false);

    // then
    assertThat(awaiter.pendingCount()).isZero();
  }
}
