/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.mailbox.api.InboxMessage;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.mailbox.api.NewInboxMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class InboxDrainerTest {

  private static final int PARTITION = 3;
  private static final String LEADER = "leader-a";
  private static final String OTHER = "leader-b";
  private static final MailboxConfig CONFIG =
      new MailboxConfig(
          Duration.ofMillis(10),
          Duration.ofMillis(100),
          Duration.ofMinutes(1),
          10,
          Duration.ofHours(1),
          Duration.ofHours(1));

  private final MutableInstantSource clock = new MutableInstantSource();
  private final MailboxStore store = new InMemoryMailboxStore(clock);
  private final List<Long> written = new ArrayList<>();

  @Test
  void shouldWriteMessagesInOrderAndAcknowledgeThem() {
    // given
    insert(1, 2, 3);
    final var drainer = drainer(store, messages -> writeAll(messages, messages.size()));

    // when
    final var result = drainer.drainOnce();

    // then
    assertThat(result).isEqualTo(new DrainResult(3, 3, false));
    assertThat(written).containsExactly(1L, 2L, 3L);
    clock.advance(Duration.ofMinutes(2));
    assertThat(store.claim(PARTITION, OTHER, 10, CONFIG.claimTimeout())).isEmpty();
  }

  @Test
  void shouldAcknowledgeOnlyAfterTheWriterReturned() {
    // given
    insert(1);
    final var calls = new ArrayList<String>();
    final var recordingStore = StoreProxy.wrap(store, Set.of(), calls);
    final var drainer =
        drainer(
            recordingStore,
            messages -> {
              calls.add("write");
              return messages.size();
            });

    // when
    drainer.drainOnce();

    // then
    assertThat(calls).containsExactly("claim", "write", "acknowledge");
  }

  @Test
  void shouldAcknowledgeOnlyTheWrittenMessagesAndReleaseTheRest() {
    // given
    insert(1, 2, 3);
    final var drainer = drainer(store, messages -> writeAll(messages, 2));

    // when
    final var result = drainer.drainOnce();

    // then
    assertThat(result).isEqualTo(new DrainResult(3, 2, false));
    final var next = store.claim(PARTITION, OTHER, 10, CONFIG.claimTimeout());
    assertThat(next).extracting(InboxMessage::requestId).containsExactly(3L);
    assertThat(next).extracting(InboxMessage::deliveryCount).containsExactly(2);
  }

  @Test
  void shouldKeepAllMessagesWhenTheWriterFails() {
    // given
    insert(1, 2, 3);
    final var drainer =
        drainer(
            store,
            messages -> {
              throw new IllegalStateException("log is closed");
            });

    // when
    final var result = drainer.drainOnce();

    // then
    assertThat(result).isEqualTo(new DrainResult(3, 0, true));
    final var next = store.claim(PARTITION, OTHER, 10, CONFIG.claimTimeout());
    assertThat(next).extracting(InboxMessage::requestId).containsExactly(1L, 2L, 3L);
  }

  @Test
  void shouldKeepMessagesForRedeliveryWhenTheAcknowledgementFails() {
    // given
    insert(1);
    final var failingStore = StoreProxy.wrap(store, Set.of("acknowledge"), new ArrayList<>());
    final var drainer = drainer(failingStore, messages -> writeAll(messages, messages.size()));

    // when
    final var result = drainer.drainOnce();

    // then
    assertThat(result.failed()).isTrue();
    final var next = store.claim(PARTITION, OTHER, 10, CONFIG.claimTimeout());
    assertThat(next).extracting(InboxMessage::deliveryCount).containsExactly(2);
  }

  @Test
  void shouldReportFailureWithoutWritingWhenTheClaimFails() {
    // given
    insert(1);
    final var failingStore = StoreProxy.wrap(store, Set.of("claim"), new ArrayList<>());
    final var drainer = drainer(failingStore, messages -> writeAll(messages, messages.size()));

    // when
    final var result = drainer.drainOnce();

    // then
    assertThat(result).isEqualTo(new DrainResult(0, 0, true));
    assertThat(result.wasIdle()).isFalse();
    assertThat(written).isEmpty();
  }

  @Test
  void shouldBeIdleWhenTheInboxIsEmpty() {
    // given
    final var drainer = drainer(store, messages -> writeAll(messages, messages.size()));

    // when
    final var result = drainer.drainOnce();

    // then
    assertThat(result.wasIdle()).isTrue();
  }

  @Test
  void shouldLetTheNextLeaderTakeMessagesAfterReleasingTheClaims() {
    // given
    insert(1);
    store.claim(PARTITION, LEADER, 10, CONFIG.claimTimeout());
    final var drainer = drainer(store, messages -> 0);

    // when
    drainer.releaseClaims();

    // then
    assertThat(store.claim(PARTITION, OTHER, 10, CONFIG.claimTimeout())).hasSize(1);
  }

  private InboxDrainer drainer(final MailboxStore drainerStore, final InboxMessageWriter writer) {
    return new InboxDrainer(drainerStore, PARTITION, LEADER, CONFIG, writer);
  }

  private int writeAll(final List<InboxMessage> messages, final int count) {
    messages.stream().limit(count).forEach(message -> written.add(message.requestId()));
    return count;
  }

  private void insert(final long... requestIds) {
    for (final long requestId : requestIds) {
      store.insert(
          new NewInboxMessage(
              PARTITION, MessageKind.CLIENT_REQUEST, "gateway-1", requestId, new byte[] {1}));
    }
  }
}
