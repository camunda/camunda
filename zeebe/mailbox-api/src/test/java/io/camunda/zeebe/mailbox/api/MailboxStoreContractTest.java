/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behaviour that every {@link MailboxStore} must have. An implementation extends this class and
 * provides a store and a way to move its clock.
 *
 * <p>Each test uses its own partition, sender and gateway ids, so the tests can share one database.
 */
public abstract class MailboxStoreContractTest {

  private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(1);
  private static final Duration LONGER_THAN_CLAIM_TIMEOUT = Duration.ofMinutes(2);

  protected MailboxStore store;
  private int partition;
  private String sender;

  /** Creates the store under test. It is called before each test. */
  protected abstract MailboxStore createStore();

  /**
   * Moves the clock of the store forward. An implementation on a real database may wait instead, as
   * long as the tests above still pass.
   */
  protected abstract void advanceTime(Duration duration);

  @BeforeEach
  void setUpStore() {
    store = createStore();
    partition = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
    sender = "sender-" + UUID.randomUUID();
  }

  // ---- Partition inbox ----

  @Test
  void shouldHandOutMessagesInInsertOrder() {
    // given
    insert(1);
    insert(2);
    insert(3);

    // when
    final var claimed = store.claim(partition, "leader-a", 10, CLAIM_TIMEOUT);

    // then
    assertThat(claimed).extracting(InboxMessage::requestId).containsExactly(1L, 2L, 3L);
    assertThat(claimed).extracting(InboxMessage::sequence).isSorted();
    assertThat(claimed).extracting(InboxMessage::deliveryCount).containsOnly(1);
  }

  @Test
  void shouldReturnTheStoredMessage() {
    // given
    final var payload = "hello".getBytes(StandardCharsets.UTF_8);
    store.insert(new NewInboxMessage(partition, MessageKind.CLIENT_REQUEST, sender, 7, payload));

    // when
    final var claimed = store.claim(partition, "leader-a", 10, CLAIM_TIMEOUT);

    // then
    assertThat(claimed).hasSize(1);
    final var message = claimed.get(0);
    assertThat(message.partitionId()).isEqualTo(partition);
    assertThat(message.kind()).isEqualTo(MessageKind.CLIENT_REQUEST);
    assertThat(message.sender()).isEqualTo(sender);
    assertThat(message.requestId()).isEqualTo(7);
    assertThat(message.payload()).isEqualTo(payload);
  }

  @Test
  void shouldNotReturnMoreMessagesThanRequested() {
    // given
    insert(1);
    insert(2);
    insert(3);

    // when
    final var first = store.claim(partition, "leader-a", 2, CLAIM_TIMEOUT);
    final var second = store.claim(partition, "leader-b", 2, CLAIM_TIMEOUT);

    // then
    assertThat(first).extracting(InboxMessage::requestId).containsExactly(1L, 2L);
    assertThat(second).extracting(InboxMessage::requestId).containsExactly(3L);
  }

  @Test
  void shouldOnlyReturnMessagesOfTheRequestedPartition() {
    // given
    final var otherPartition = partition == Integer.MAX_VALUE ? partition - 1 : partition + 1;
    insert(1);
    store.insert(
        new NewInboxMessage(otherPartition, MessageKind.CLIENT_REQUEST, sender, 2, new byte[] {1}));

    // when
    final var claimed = store.claim(partition, "leader-a", 10, CLAIM_TIMEOUT);

    // then
    assertThat(claimed).extracting(InboxMessage::requestId).containsExactly(1L);
  }

  @Test
  void shouldNotHandOutClaimedMessagesToAnotherClaimer() {
    // given
    insert(1);
    store.claim(partition, "leader-a", 10, CLAIM_TIMEOUT);

    // when
    final var claimedByOther = store.claim(partition, "leader-b", 10, CLAIM_TIMEOUT);

    // then
    assertThat(claimedByOther).isEmpty();
  }

  @Test
  void shouldHandOutMessageAgainAfterClaimTimeout() {
    // given
    insert(1);
    store.claim(partition, "leader-a", 10, CLAIM_TIMEOUT);

    // when
    advanceTime(LONGER_THAN_CLAIM_TIMEOUT);
    final var claimedByOther = store.claim(partition, "leader-b", 10, CLAIM_TIMEOUT);

    // then
    assertThat(claimedByOther).extracting(InboxMessage::requestId).containsExactly(1L);
    assertThat(claimedByOther).extracting(InboxMessage::deliveryCount).containsExactly(2);
  }

  @Test
  void shouldRemoveAcknowledgedMessages() {
    // given
    insert(1);
    final var claimed = store.claim(partition, "leader-a", 10, CLAIM_TIMEOUT);

    // when
    final var removed = store.acknowledge(partition, "leader-a", sequencesOf(claimed));
    advanceTime(LONGER_THAN_CLAIM_TIMEOUT);

    // then
    assertThat(removed).isEqualTo(1);
    assertThat(store.claim(partition, "leader-b", 10, CLAIM_TIMEOUT)).isEmpty();
  }

  @Test
  void shouldIgnoreAcknowledgementFromClaimerWithoutClaim() {
    // given
    insert(1);
    final var claimed = store.claim(partition, "leader-a", 10, CLAIM_TIMEOUT);

    // when
    final var removed = store.acknowledge(partition, "leader-b", sequencesOf(claimed));

    // then
    assertThat(removed).isZero();
    advanceTime(LONGER_THAN_CLAIM_TIMEOUT);
    assertThat(store.claim(partition, "leader-b", 10, CLAIM_TIMEOUT)).hasSize(1);
  }

  @Test
  void shouldIgnoreLateAcknowledgementAfterAnotherClaimerTookTheMessage() {
    // given
    insert(1);
    final var firstClaim = store.claim(partition, "leader-a", 10, CLAIM_TIMEOUT);
    advanceTime(LONGER_THAN_CLAIM_TIMEOUT);
    final var secondClaim = store.claim(partition, "leader-b", 10, CLAIM_TIMEOUT);

    // when
    final var lateAck = store.acknowledge(partition, "leader-a", sequencesOf(firstClaim));
    final var ack = store.acknowledge(partition, "leader-b", sequencesOf(secondClaim));

    // then
    assertThat(lateAck).isZero();
    assertThat(ack).isEqualTo(1);
  }

  @Test
  void shouldHandOutReleasedMessagesImmediately() {
    // given
    insert(1);
    store.claim(partition, "leader-a", 10, CLAIM_TIMEOUT);

    // when
    final var released = store.releaseClaims(partition, "leader-a");
    final var claimedByOther = store.claim(partition, "leader-b", 10, CLAIM_TIMEOUT);

    // then
    assertThat(released).isEqualTo(1);
    assertThat(claimedByOther).extracting(InboxMessage::deliveryCount).containsExactly(2);
  }

  @Test
  void shouldNotReleaseClaimsOfAnotherClaimer() {
    // given
    insert(1);
    store.claim(partition, "leader-a", 10, CLAIM_TIMEOUT);

    // when
    final var released = store.releaseClaims(partition, "leader-b");

    // then
    assertThat(released).isZero();
    assertThat(store.claim(partition, "leader-b", 10, CLAIM_TIMEOUT)).isEmpty();
  }

  @Test
  void shouldIgnoreInsertOfAnExistingKey() {
    // given
    final var first =
        new NewInboxMessage(
            partition,
            MessageKind.CLIENT_REQUEST,
            sender,
            1,
            "first".getBytes(StandardCharsets.UTF_8));
    final var second =
        new NewInboxMessage(
            partition,
            MessageKind.CLIENT_REQUEST,
            sender,
            1,
            "second".getBytes(StandardCharsets.UTF_8));

    // when
    final var firstAdded = store.insert(first);
    final var secondAdded = store.insert(second);

    // then
    assertThat(firstAdded).isTrue();
    assertThat(secondAdded).isFalse();
    final var claimed = store.claim(partition, "leader-a", 10, CLAIM_TIMEOUT);
    assertThat(claimed).hasSize(1);
    assertThat(claimed.get(0).payload()).isEqualTo("first".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void shouldAcceptTheSameRequestIdFromDifferentSenders() {
    // given
    final var otherSender = "other-" + UUID.randomUUID();

    // when
    final var first = store.insert(message(sender, 1));
    final var second = store.insert(message(otherSender, 1));

    // then
    assertThat(first).isTrue();
    assertThat(second).isTrue();
  }

  // ---- Gateway outbox ----

  @Test
  void shouldReturnAndRemoveStoredResponse() {
    // given
    final var gateway = newGateway();
    final var payload = "response".getBytes(StandardCharsets.UTF_8);
    store.putResponse(new OutboxResponse(gateway, 5, payload));

    // when
    final var first = store.takeResponses(gateway, Set.of(5L));
    final var second = store.takeResponses(gateway, Set.of(5L));

    // then
    assertThat(first).hasSize(1);
    assertThat(first.get(0).requestId()).isEqualTo(5);
    assertThat(first.get(0).gatewayId()).isEqualTo(gateway);
    assertThat(first.get(0).payload()).isEqualTo(payload);
    assertThat(second).isEmpty();
  }

  @Test
  void shouldOnlyReturnResponsesThatWereAskedFor() {
    // given
    final var gateway = newGateway();
    store.putResponse(new OutboxResponse(gateway, 1, new byte[] {1}));
    store.putResponse(new OutboxResponse(gateway, 2, new byte[] {2}));

    // when
    final var taken = store.takeResponses(gateway, Set.of(2L, 3L));

    // then
    assertThat(taken).extracting(OutboxResponse::requestId).containsExactly(2L);
    assertThat(store.takeResponses(gateway, Set.of(1L))).hasSize(1);
  }

  @Test
  void shouldNotReturnResponsesOfAnotherGateway() {
    // given
    final var gateway = newGateway();
    final var otherGateway = newGateway();
    store.putResponse(new OutboxResponse(gateway, 1, new byte[] {1}));

    // when
    final var taken = store.takeResponses(otherGateway, Set.of(1L));

    // then
    assertThat(taken).isEmpty();
    assertThat(store.takeResponses(gateway, Set.of(1L))).hasSize(1);
  }

  @Test
  void shouldKeepTheFirstResponseOfARequest() {
    // given
    final var gateway = newGateway();
    final var first = store.putResponse(new OutboxResponse(gateway, 1, new byte[] {1}));
    final var second = store.putResponse(new OutboxResponse(gateway, 1, new byte[] {2}));

    // when
    final var taken = store.takeResponses(gateway, Set.of(1L));

    // then
    assertThat(first).isTrue();
    assertThat(second).isFalse();
    assertThat(taken).hasSize(1);
    assertThat(taken.get(0).payload()).isEqualTo(new byte[] {1});
  }

  // ---- Gateway registry ----

  @Test
  void shouldReturnTheSameStreamIdForTheSameGateway() {
    // given
    final var gateway = newGateway();

    // when
    final var first = store.registerGateway(gateway);
    final var second = store.registerGateway(gateway);

    // then
    assertThat(second).isEqualTo(first);
  }

  @Test
  void shouldReturnDifferentStreamIdsForDifferentGateways() {
    // when
    final var first = store.registerGateway(newGateway());
    final var second = store.registerGateway(newGateway());

    // then
    assertThat(second).isNotEqualTo(first);
  }

  @Test
  void shouldFindARegisteredGatewayByStreamId() {
    // given
    final var gateway = newGateway();
    final var streamId = store.registerGateway(gateway);

    // when
    final var found = store.findGateway(streamId);

    // then
    assertThat(found).contains(gateway);
  }

  @Test
  void shouldNotMapAnotherStreamIdToTheGateway() {
    // given
    final var gateway = newGateway();
    final var streamId = store.registerGateway(gateway);
    final var otherStreamId = streamId == Integer.MAX_VALUE ? streamId - 1 : streamId + 1;

    // when
    final var found = store.findGateway(otherStreamId);

    // then
    assertThat(found.orElse(null)).isNotEqualTo(gateway);
  }

  // ---- Cleanup ----

  @Test
  void shouldDeleteExpiredMessagesAndResponsesOnly() {
    // given
    final var gateway = newGateway();
    insert(1);
    store.putResponse(new OutboxResponse(gateway, 1, new byte[] {1}));
    advanceTime(LONGER_THAN_CLAIM_TIMEOUT);
    insert(2);
    store.putResponse(new OutboxResponse(gateway, 2, new byte[] {2}));

    // when
    final var result = store.deleteExpired(CLAIM_TIMEOUT, CLAIM_TIMEOUT);

    // then
    assertThat(result.inboxDeleted()).isGreaterThanOrEqualTo(1);
    assertThat(result.outboxDeleted()).isGreaterThanOrEqualTo(1);
    final var remaining = store.claim(partition, "leader-a", 10, CLAIM_TIMEOUT);
    assertThat(remaining).extracting(InboxMessage::requestId).containsExactly(2L);
    final var responses = store.takeResponses(gateway, Set.of(1L, 2L));
    assertThat(responses).extracting(OutboxResponse::requestId).containsExactly(2L);
  }

  // ---- helpers ----

  private boolean insert(final long requestId) {
    return store.insert(message(sender, requestId));
  }

  private NewInboxMessage message(final String messageSender, final long requestId) {
    return new NewInboxMessage(
        partition, MessageKind.CLIENT_REQUEST, messageSender, requestId, new byte[] {1});
  }

  private static GatewayId newGateway() {
    return new GatewayId("gateway-" + UUID.randomUUID());
  }

  private static List<Long> sequencesOf(final List<InboxMessage> messages) {
    return messages.stream().map(InboxMessage::sequence).toList();
  }
}
