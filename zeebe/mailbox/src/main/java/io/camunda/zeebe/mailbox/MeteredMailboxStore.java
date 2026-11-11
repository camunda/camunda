/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import io.camunda.zeebe.mailbox.api.CleanupResult;
import io.camunda.zeebe.mailbox.api.GatewayId;
import io.camunda.zeebe.mailbox.api.InboxMessage;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.mailbox.api.NewInboxMessage;
import io.camunda.zeebe.mailbox.api.OutboxResponse;
import io.camunda.zeebe.mailbox.api.PartitionLeader;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A mailbox store that records metrics about the calls to another one. Wrap the store once where it
 * is loaded, so that every user of it is measured.
 *
 * <p>Besides the latency of each call, it counts what goes through the inbox: the messages handed
 * out, the ones handed out again because an earlier claimer did not acknowledge them, which may be
 * duplicates, the ones acknowledged, and the rows that the cleanup removed because nobody collected
 * them in time, which are lost requests or responses.
 */
public final class MeteredMailboxStore implements MailboxStore {

  public static final String STORE_LATENCY = "zeebe.mailbox.store.latency";
  public static final String CLAIMED = "zeebe.mailbox.inbox.claimed";
  public static final String REDELIVERED = "zeebe.mailbox.inbox.redelivered";
  public static final String ACKNOWLEDGED = "zeebe.mailbox.inbox.acknowledged";
  public static final String EXPIRED = "zeebe.mailbox.expired";

  private final MailboxStore delegate;
  private final MeterRegistry registry;
  private final StoreCallTimer timer;

  public MeteredMailboxStore(final MailboxStore delegate, final MeterRegistry registry) {
    this.delegate = delegate;
    this.registry = registry;
    timer =
        new StoreCallTimer(
            registry, STORE_LATENCY, "The latency of the calls to the mailbox store");
  }

  @Override
  public boolean insert(final NewInboxMessage message) {
    return timer.time("insert", () -> delegate.insert(message));
  }

  @Override
  public List<InboxMessage> claim(
      final int partitionId,
      final String claimerId,
      final Set<MessageKind> kinds,
      final int maxMessages,
      final Duration claimTimeout) {
    final var claimed =
        timer.time(
            "claim",
            () -> delegate.claim(partitionId, claimerId, kinds, maxMessages, claimTimeout));
    for (final var message : claimed) {
      count(CLAIMED, "Inbox messages handed out to a claimer", message);
      if (message.deliveryCount() > 1) {
        count(REDELIVERED, "Inbox messages handed out again, which may be duplicates", message);
      }
    }
    return claimed;
  }

  @Override
  public int acknowledge(
      final int partitionId, final String claimerId, final Collection<Long> sequences) {
    final int acknowledged =
        timer.time("acknowledge", () -> delegate.acknowledge(partitionId, claimerId, sequences));
    Counter.builder(ACKNOWLEDGED)
        .description("Inbox messages acknowledged, and so removed from the inbox")
        .tag("partition", String.valueOf(partitionId))
        .register(registry)
        .increment(acknowledged);
    return acknowledged;
  }

  @Override
  public int releaseClaims(final int partitionId, final String claimerId) {
    return timer.time("releaseClaims", () -> delegate.releaseClaims(partitionId, claimerId));
  }

  @Override
  public boolean putResponse(final OutboxResponse response) {
    return timer.time("putResponse", () -> delegate.putResponse(response));
  }

  @Override
  public List<OutboxResponse> takeResponses(final GatewayId gatewayId, final Set<Long> requestIds) {
    return timer.time("takeResponses", () -> delegate.takeResponses(gatewayId, requestIds));
  }

  @Override
  public boolean putLeader(final PartitionLeader leader) {
    return timer.time("putLeader", () -> delegate.putLeader(leader));
  }

  @Override
  public boolean removeLeader(final int partitionId, final String nodeId, final long term) {
    return timer.time("removeLeader", () -> delegate.removeLeader(partitionId, nodeId, term));
  }

  @Override
  public Optional<PartitionLeader> findLeader(final int partitionId) {
    return timer.time("findLeader", () -> delegate.findLeader(partitionId));
  }

  @Override
  public List<PartitionLeader> findLeaders() {
    return timer.time("findLeaders", delegate::findLeaders);
  }

  @Override
  public CleanupResult deleteExpired(final Duration inboxTtl, final Duration outboxTtl) {
    final var result =
        timer.time("deleteExpired", () -> delegate.deleteExpired(inboxTtl, outboxTtl));
    expired("inbox").increment(result.inboxDeleted());
    expired("outbox").increment(result.outboxDeleted());
    return result;
  }

  private void count(final String name, final String description, final InboxMessage message) {
    Counter.builder(name)
        .description(description)
        .tag("partition", String.valueOf(message.partitionId()))
        .tag("kind", message.kind().name())
        .register(registry)
        .increment();
  }

  private Counter expired(final String table) {
    return Counter.builder(EXPIRED)
        .description("Mailbox rows removed because nobody collected them in time")
        .tag("table", table)
        .register(registry);
  }
}
