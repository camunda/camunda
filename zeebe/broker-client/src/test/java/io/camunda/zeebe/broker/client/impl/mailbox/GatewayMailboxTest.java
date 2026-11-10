/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.broker.client.api.mailbox.GatewayMailboxSetup;
import io.camunda.zeebe.mailbox.InMemoryMailboxStore;
import io.camunda.zeebe.mailbox.api.GatewayId;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.mailbox.api.OutboxResponse;
import io.camunda.zeebe.mailbox.api.PartitionLeader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class GatewayMailboxTest {

  private static final String GATEWAY = "gateway-1";
  private static final MailboxConfig CONFIG =
      new MailboxConfig(
          Duration.ofMillis(5),
          Duration.ofMillis(20),
          Duration.ofMinutes(1),
          10,
          Duration.ofHours(1),
          Duration.ofHours(1));

  private final InMemoryMailboxStore store = new InMemoryMailboxStore();
  private GatewayMailbox mailbox;

  @AfterEach
  void tearDown() {
    if (mailbox != null) {
      mailbox.close();
    }
  }

  @Test
  void shouldFindTheRecordedLeaderAfterARefresh() {
    // given
    mailbox = start(store);
    store.putLeader(new PartitionLeader(1, "us_0", "us", 3));

    // when
    mailbox.refreshLeaders();

    // then
    assertThat(mailbox.recordedLeader(1)).contains(new PartitionLeader(1, "us_0", "us", 3));
  }

  @Test
  void shouldFindNoLeaderWhenNoneIsRecorded() {
    // given
    mailbox = start(store);
    mailbox.refreshLeaders();

    // when / then
    assertThat(mailbox.recordedLeader(1)).isEmpty();
  }

  @Test
  void shouldNotSeeALeaderBeforeTheRefresh() {
    // given
    mailbox = start(store);
    mailbox.refreshLeaders();

    // when
    store.putLeader(new PartitionLeader(1, "us_0", "us", 3));

    // then
    assertThat(mailbox.recordedLeader(1)).isEmpty();
  }

  @Test
  void shouldForgetALeaderThatWasRemoved() {
    // given
    mailbox = start(store);
    store.putLeader(new PartitionLeader(1, "us_0", "us", 3));
    mailbox.refreshLeaders();
    store.removeLeader(1, "us_0", 3);

    // when
    mailbox.refreshLeaders();

    // then
    assertThat(mailbox.recordedLeader(1)).isEmpty();
  }

  @Test
  void shouldKeepTheLeadersWhenTheStoreFailsOnRefresh() {
    // given
    final var failing = new AtomicBoolean();
    mailbox = start(failingWhen(failing, "findLeaders"));
    store.putLeader(new PartitionLeader(1, "us_0", "us", 3));
    mailbox.refreshLeaders();

    // when
    failing.set(true);
    mailbox.refreshLeaders();

    // then
    assertThat(mailbox.recordedLeader(1)).isPresent();
  }

  @Test
  void shouldRefreshTheLeadersRegularly() {
    // given
    mailbox = start(store);

    // when
    store.putLeader(new PartitionLeader(1, "us_0", "us", 3));

    // then
    await().atMost(Duration.ofSeconds(10)).until(() -> mailbox.recordedLeader(1).isPresent());
  }

  @Test
  void shouldPutTheRequestIntoTheInboxOfThePartition() {
    // given
    mailbox = start(store);

    // when
    mailbox.send(2, new byte[] {1, 2}, Duration.ofSeconds(30));

    // then
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              final var messages = store.claim(2, "leader", 10, Duration.ofMinutes(1));
              assertThat(messages).hasSize(1);
              assertThat(messages.get(0).kind()).isEqualTo(MessageKind.CLIENT_REQUEST);
              assertThat(messages.get(0).sender()).isEqualTo(GATEWAY);
              assertThat(messages.get(0).payload()).containsExactly(1, 2);
            });
  }

  @Test
  void shouldReturnTheResponseThatTheLeaderPutIntoTheOutbox() throws Exception {
    // given
    mailbox = start(store);
    final var response = mailbox.send(2, new byte[] {1}, Duration.ofSeconds(30));
    final var request =
        await()
            .atMost(Duration.ofSeconds(5))
            .until(() -> store.claim(2, "leader", 10, Duration.ofMinutes(1)), m -> !m.isEmpty())
            .get(0);

    // when
    store.putResponse(
        new OutboxResponse(new GatewayId(GATEWAY), request.requestId(), new byte[] {9}));

    // then
    assertThat(response.get(5, java.util.concurrent.TimeUnit.SECONDS)).containsExactly(9);
  }

  @Test
  void shouldFailWhenNoResponseComesInTime() {
    // given
    mailbox = start(store);

    // when
    final var response = mailbox.send(2, new byte[] {1}, Duration.ofMillis(100));

    // then
    assertThatThrownBy(() -> response.get(5, java.util.concurrent.TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(TimeoutException.class);
  }

  @Test
  void shouldFailWhenTheRequestCannotBePutIntoTheInbox() {
    // given
    mailbox = start(failingOn("insert"));

    // when
    final var response = mailbox.send(2, new byte[] {1}, Duration.ofSeconds(30));

    // then
    assertThatThrownBy(() -> response.get(5, java.util.concurrent.TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldGiveEveryRequestItsOwnResponse() throws Exception {
    // given
    mailbox = start(store);
    final var first = mailbox.send(2, new byte[] {1}, Duration.ofSeconds(30));
    final var second = mailbox.send(2, new byte[] {2}, Duration.ofSeconds(30));
    final var requests =
        await()
            .atMost(Duration.ofSeconds(5))
            .until(() -> store.claim(2, "leader", 10, Duration.ofMinutes(1)), m -> !m.isEmpty());
    assertThat(requests).hasSize(2);

    // when - the leader answers the second request first, with the payload of its request
    for (final var request : requests.reversed()) {
      store.putResponse(
          new OutboxResponse(new GatewayId(GATEWAY), request.requestId(), request.payload()));
    }

    // then
    assertThat(first.get(5, java.util.concurrent.TimeUnit.SECONDS)).containsExactly(1);
    assertThat(second.get(5, java.util.concurrent.TimeUnit.SECONDS)).containsExactly(2);
    assertThat(store.takeResponses(new GatewayId(GATEWAY), Set.of())).isEmpty();
  }

  private static GatewayMailbox start(final MailboxStore store) {
    final var mailbox =
        new GatewayMailbox(new GatewayMailboxSetup(store, CONFIG, GATEWAY, Runnable::run));
    mailbox.start();
    return mailbox;
  }

  /** A store that works as the in-memory store, except that one method fails. */
  private MailboxStore failingOn(final String method) {
    return failingWhen(new AtomicBoolean(true), method);
  }

  private MailboxStore failingWhen(final AtomicBoolean failing, final String method) {
    return (MailboxStore)
        Proxy.newProxyInstance(
            MailboxStore.class.getClassLoader(),
            new Class<?>[] {MailboxStore.class},
            (proxy, called, args) -> {
              if (failing.get() && called.getName().equals(method)) {
                throw new MailboxStoreException("down", new RuntimeException());
              }
              try {
                return called.invoke(store, args);
              } catch (final InvocationTargetException e) {
                throw e.getCause();
              }
            });
  }
}
