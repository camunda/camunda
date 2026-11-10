/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.mailbox.api.MessageKind;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

final class InboxSenderTest {

  private static final int PARTITION = 2;

  private final MutableInstantSource clock = new MutableInstantSource();
  private final InMemoryMailboxStore store = new InMemoryMailboxStore(clock);

  @Test
  void shouldPutTheMessageIntoTheInboxOfThePartition() {
    // given
    final var sender = new InboxSender(store, "broker-1", Runnable::run, clock);
    final var requestId = sender.nextRequestId();

    // when
    final var sent =
        sender.send(PARTITION, MessageKind.INTER_PARTITION_COMMAND, requestId, new byte[] {9});

    // then
    assertThat(sent).isCompletedWithValue(true);
    final var claimed = store.claim(PARTITION, "leader", 10, Duration.ofMinutes(1));
    assertThat(claimed).hasSize(1);
    assertThat(claimed.get(0).sender()).isEqualTo("broker-1");
    assertThat(claimed.get(0).kind()).isEqualTo(MessageKind.INTER_PARTITION_COMMAND);
    assertThat(claimed.get(0).requestId()).isEqualTo(requestId);
    assertThat(claimed.get(0).payload()).isEqualTo(new byte[] {9});
  }

  @Test
  void shouldNotInsertOnTheCallingThread() {
    // given
    final var queued = new ArrayDeque<Runnable>();
    final var sender = new InboxSender(store, "broker-1", queued::add, clock);

    // when
    final var sent =
        sender.send(PARTITION, MessageKind.CLIENT_REQUEST, sender.nextRequestId(), new byte[0]);

    // then
    assertThat(sent).isNotDone();
    assertThat(store.claim(PARTITION, "leader", 10, Duration.ofMinutes(1))).isEmpty();
    queued.poll().run();
    assertThat(sent).isCompletedWithValue(true);
  }

  @Test
  void shouldHandOutDifferentRequestIds() {
    // given
    final var sender = new InboxSender(store, "broker-1", Runnable::run, clock);

    // when
    final var first = sender.nextRequestId();
    final var second = sender.nextRequestId();

    // then
    assertThat(second).isGreaterThan(first);
  }

  @Test
  void shouldStartRequestIdsAfterTheIdsOfAnEarlierRun() {
    // given
    final var earlier = new InboxSender(store, "broker-1", Runnable::run, clock);
    final var lastOfEarlierRun = earlier.nextRequestId();
    clock.advance(Duration.ofSeconds(1));

    // when
    final var afterRestart = new InboxSender(store, "broker-1", Runnable::run, clock);

    // then
    assertThat(afterRestart.nextRequestId()).isGreaterThan(lastOfEarlierRun);
  }

  @Test
  void shouldReportFailureWhenTheStoreFails() {
    // given
    final var failingStore = StoreProxy.wrap(store, Set.of("insert"), new ArrayList<>());
    final var sender = new InboxSender(failingStore, "broker-1", Runnable::run, clock);

    // when
    final var sent =
        sender.send(PARTITION, MessageKind.CLIENT_REQUEST, sender.nextRequestId(), new byte[0]);

    // then
    assertThat(sent).isCompletedWithValue(false);
  }

  @Test
  void shouldReportFailureWhenTheExecutorRejectsTheInsert() {
    // given
    final Executor rejecting =
        task -> {
          throw new RejectedExecutionException("closed");
        };
    final var sender = new InboxSender(store, "broker-1", rejecting, clock);

    // when
    final var sent =
        sender.send(PARTITION, MessageKind.CLIENT_REQUEST, sender.nextRequestId(), new byte[0]);

    // then
    assertThat(sent).isCompletedWithValue(false);
  }
}
