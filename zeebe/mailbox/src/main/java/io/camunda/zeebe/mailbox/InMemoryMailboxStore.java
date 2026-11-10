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
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * A {@link MailboxStore} in memory. It is for tests and for a proof of concept in one process. It
 * keeps nothing across a restart and cannot be shared by several processes.
 *
 * <p>All methods are synchronized, so each takes effect atomically.
 */
public final class InMemoryMailboxStore implements MailboxStore {

  private final InstantSource clock;
  private final Map<Integer, TreeMap<Long, InboxEntry>> inboxes = new HashMap<>();
  private final Map<InboxKey, Long> sequenceByKey = new HashMap<>();
  private final Map<GatewayId, Map<Long, OutboxEntry>> outboxes = new HashMap<>();
  private final Map<Integer, PartitionLeader> leaders = new HashMap<>();
  private long nextSequence = 1;

  public InMemoryMailboxStore() {
    this(InstantSource.system());
  }

  /**
   * @param clock the clock of the store. A test passes a clock that it can move.
   */
  public InMemoryMailboxStore(final InstantSource clock) {
    this.clock = clock;
  }

  @Override
  public synchronized boolean insert(final NewInboxMessage message) {
    final var key = new InboxKey(message.partitionId(), message.sender(), message.requestId());
    if (sequenceByKey.containsKey(key)) {
      return false;
    }
    final var sequence = nextSequence++;
    sequenceByKey.put(key, sequence);
    inboxes
        .computeIfAbsent(message.partitionId(), id -> new TreeMap<>())
        .put(sequence, new InboxEntry(sequence, key, message, clock.instant()));
    return true;
  }

  @Override
  public synchronized List<InboxMessage> claim(
      final int partitionId,
      final String claimerId,
      final Set<MessageKind> kinds,
      final int maxMessages,
      final Duration claimTimeout) {
    if (claimerId.isBlank()) {
      throw new IllegalArgumentException("claimerId must not be blank");
    }
    if (kinds.isEmpty()) {
      throw new IllegalArgumentException("kinds must not be empty");
    }
    if (maxMessages < 1) {
      throw new IllegalArgumentException("maxMessages must be at least 1");
    }

    final var now = clock.instant();
    final var claimed = new ArrayList<InboxMessage>();
    for (final var entry : inboxes.getOrDefault(partitionId, new TreeMap<>()).values()) {
      if (claimed.size() == maxMessages) {
        break;
      }
      if (kinds.contains(entry.message.kind()) && entry.isAvailable(now)) {
        entry.claimerId = claimerId;
        entry.claimExpiresAt = now.plus(claimTimeout);
        entry.deliveryCount++;
        claimed.add(entry.toMessage());
      }
    }
    return claimed;
  }

  @Override
  public synchronized int acknowledge(
      final int partitionId, final String claimerId, final Collection<Long> sequences) {
    final var inbox = inboxes.get(partitionId);
    if (inbox == null) {
      return 0;
    }
    var removed = 0;
    for (final long sequence : new HashSet<>(sequences)) {
      final var entry = inbox.get(sequence);
      if (entry != null && claimerId.equals(entry.claimerId)) {
        inbox.remove(sequence);
        sequenceByKey.remove(entry.key);
        removed++;
      }
    }
    return removed;
  }

  @Override
  public synchronized int releaseClaims(final int partitionId, final String claimerId) {
    var released = 0;
    for (final var entry : inboxes.getOrDefault(partitionId, new TreeMap<>()).values()) {
      if (claimerId.equals(entry.claimerId)) {
        entry.claimerId = null;
        entry.claimExpiresAt = null;
        released++;
      }
    }
    return released;
  }

  @Override
  public synchronized boolean putResponse(final OutboxResponse response) {
    final var outbox = outboxes.computeIfAbsent(response.gatewayId(), id -> new HashMap<>());
    if (outbox.containsKey(response.requestId())) {
      return false;
    }
    outbox.put(response.requestId(), new OutboxEntry(response.payload().clone(), clock.instant()));
    return true;
  }

  @Override
  public synchronized List<OutboxResponse> takeResponses(
      final GatewayId gatewayId, final Set<Long> requestIds) {
    final var outbox = outboxes.get(gatewayId);
    if (outbox == null) {
      return List.of();
    }
    final var taken = new ArrayList<OutboxResponse>();
    for (final long requestId : requestIds) {
      final var entry = outbox.remove(requestId);
      if (entry != null) {
        taken.add(new OutboxResponse(gatewayId, requestId, entry.payload.clone()));
      }
    }
    return taken;
  }

  @Override
  public synchronized boolean putLeader(final PartitionLeader leader) {
    final var current = leaders.get(leader.partitionId());
    if (current != null && current.term() > leader.term()) {
      return false;
    }
    leaders.put(leader.partitionId(), leader);
    return true;
  }

  @Override
  public synchronized boolean removeLeader(
      final int partitionId, final String nodeId, final long term) {
    final var current = leaders.get(partitionId);
    if (current == null || current.term() != term || !current.nodeId().equals(nodeId)) {
      return false;
    }
    leaders.remove(partitionId);
    return true;
  }

  @Override
  public synchronized Optional<PartitionLeader> findLeader(final int partitionId) {
    return Optional.ofNullable(leaders.get(partitionId));
  }

  @Override
  public synchronized List<PartitionLeader> findLeaders() {
    return List.copyOf(leaders.values());
  }

  @Override
  public synchronized CleanupResult deleteExpired(
      final Duration inboxTtl, final Duration outboxTtl) {
    final var now = clock.instant();
    final var inboxCutoff = now.minus(inboxTtl);
    final var outboxCutoff = now.minus(outboxTtl);

    var inboxDeleted = 0;
    for (final var inbox : inboxes.values()) {
      final var iterator = inbox.values().iterator();
      while (iterator.hasNext()) {
        final var entry = iterator.next();
        if (!entry.createdAt.isAfter(inboxCutoff)) {
          iterator.remove();
          sequenceByKey.remove(entry.key);
          inboxDeleted++;
        }
      }
    }

    var outboxDeleted = 0;
    for (final var outbox : outboxes.values()) {
      final var before = outbox.size();
      outbox.values().removeIf(entry -> !entry.createdAt.isAfter(outboxCutoff));
      outboxDeleted += before - outbox.size();
    }
    return new CleanupResult(inboxDeleted, outboxDeleted);
  }

  private record InboxKey(int partitionId, String sender, long requestId) {}

  private record OutboxEntry(byte[] payload, Instant createdAt) {}

  private static final class InboxEntry {
    private final long sequence;
    private final InboxKey key;
    private final NewInboxMessage message;
    private final Instant createdAt;
    private @Nullable String claimerId;
    private @Nullable Instant claimExpiresAt;
    private int deliveryCount;

    private InboxEntry(
        final long sequence,
        final InboxKey key,
        final NewInboxMessage message,
        final Instant createdAt) {
      this.sequence = sequence;
      this.key = key;
      this.message = message;
      this.createdAt = createdAt;
    }

    private boolean isAvailable(final Instant now) {
      return claimerId == null || (claimExpiresAt != null && !claimExpiresAt.isAfter(now));
    }

    private InboxMessage toMessage() {
      return new InboxMessage(
          sequence,
          message.partitionId(),
          message.kind(),
          message.sender(),
          message.requestId(),
          message.payload().clone(),
          deliveryCount);
    }
  }
}
