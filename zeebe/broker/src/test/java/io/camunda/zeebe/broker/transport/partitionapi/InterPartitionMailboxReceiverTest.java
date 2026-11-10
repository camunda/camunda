/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.mailbox.InMemoryMailboxStore;
import io.camunda.zeebe.mailbox.api.InboxMessage;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.mailbox.api.NewInboxMessage;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class InterPartitionMailboxReceiverTest {

  private static final int PARTITION = 4;
  private static final MailboxConfig CONFIG =
      new MailboxConfig(
          Duration.ofMillis(5),
          Duration.ofMillis(20),
          Duration.ofMinutes(1),
          10,
          Duration.ofHours(1),
          Duration.ofHours(1));

  private final InMemoryMailboxStore store = new InMemoryMailboxStore();
  private final List<Byte> handled = new CopyOnWriteArrayList<>();
  private final AtomicBoolean logTakesCommands = new AtomicBoolean(true);
  private final java.util.concurrent.ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor();

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void shouldWriteTheCommandsInTheInboxToTheLogInOrderAndRemoveThem() throws Exception {
    // given
    insert(1, 2, 3);
    final var receiver = receiver();

    // when
    receiver.start();
    await().atMost(Duration.ofSeconds(5)).until(() -> handled.size() >= 3);
    receiver.close();
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

    // then
    assertThat(handled).containsExactly((byte) 1, (byte) 2, (byte) 3);
    assertThat(store.claim(PARTITION, "next-leader", 10, Duration.ofMinutes(1))).isEmpty();
  }

  @Test
  void shouldPickUpCommandsThatArriveLater() {
    // given
    final var receiver = receiver();
    receiver.start();

    // when
    insert(7);

    // then
    await().atMost(Duration.ofSeconds(5)).until(() -> handled.contains((byte) 7));
    receiver.close();
  }

  @Test
  void shouldKeepCommandsThatTheLogDoesNotTakeAndReleaseThemOnClose() throws Exception {
    // given
    logTakesCommands.set(false);
    insert(1);
    final var receiver = receiver();

    // when
    receiver.start();
    await().atMost(Duration.ofSeconds(5)).until(() -> !handled.isEmpty());
    receiver.close();
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

    // then - the next leader gets the command at once, not after the claim times out
    final var next = store.claim(PARTITION, "next-leader", 10, Duration.ofMinutes(1));
    assertThat(next).extracting(InboxMessage::requestId).containsExactly(1L);
  }

  private InterPartitionMailboxReceiver receiver() {
    return new InterPartitionMailboxReceiver(
        (sender, message) -> {
          handled.add(message[0]);
          return CompletableActorFuture.completed(logTakesCommands.get());
        },
        store,
        PARTITION,
        "leader-1",
        CONFIG,
        executor);
  }

  private void insert(final int... markers) {
    for (final int marker : markers) {
      store.insert(
          new NewInboxMessage(
              PARTITION,
              MessageKind.INTER_PARTITION_COMMAND,
              "sender-1",
              marker,
              new byte[] {(byte) marker}));
    }
  }
}
