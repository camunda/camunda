/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api;

import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Storage of the cross-region mailbox. It holds a {@code partition inbox} for each partition, a
 * {@code gateway outbox} for responses, and a record of the leader of each partition.
 *
 * <p>An implementation must pass {@code MailboxStoreContractTest} from the test-jar of this module.
 *
 * <h2>Rules for all implementations</h2>
 *
 * <ul>
 *   <li><b>Blocking.</b> Calls may block on I/O. Callers must not call the store from a thread that
 *       must not block, for example an actor thread.
 *   <li><b>Time.</b> The store decides what time it is. Claim timeouts and expiry use the clock of
 *       the store, for example the database clock, and never a clock of the caller. This keeps the
 *       callers independent of their clock differences.
 *   <li><b>Atomic.</b> Each operation takes effect completely or not at all, also when several
 *       callers run it at the same time.
 *   <li><b>Repeatable.</b> A caller can repeat an operation after a {@link MailboxStoreException}.
 *       Repeating an insert is safe as long as the message is still in the inbox.
 *   <li><b>No schema in this module.</b> How the data is stored is up to the implementation.
 * </ul>
 */
public interface MailboxStore {

  // ---- Partition inbox ----

  /**
   * Adds a message to the inbox of its partition. The store assigns the sequence number.
   *
   * @param message the message to add
   * @return true if the message was added, false if the inbox already contains a message with the
   *     same key {@code (partitionId, sender, requestId)}. The message is then left unchanged. A
   *     message that was already acknowledged is gone, so repeating its insert adds it again.
   */
  boolean insert(NewInboxMessage message);

  /**
   * Hands out the oldest messages of a partition that nobody holds a valid claim on, and records a
   * claim on them for the claimer.
   *
   * <p>A message is available if it has no claim, or its claim is older than the claim timeout, or
   * its claim was released. Messages are returned in the order of their sequence. A message that is
   * handed out again has a higher {@link InboxMessage#deliveryCount()}.
   *
   * <p>Only messages of the given kinds are handed out. This lets one inbox serve several
   * consumers, for example one that writes commands from other partitions to the log and one that
   * handles requests from gateways, each with a claimer id of its own.
   *
   * @param partitionId the partition whose inbox is read
   * @param claimerId identifies the caller, for example the node that leads the partition. Not
   *     blank. Claims are released for a claimer id, so consumers that run side by side need
   *     different ids.
   * @param kinds the kinds of messages to hand out, at least one
   * @param maxMessages the most messages to return, at least 1
   * @param claimTimeout how long the claim stays valid
   * @return the claimed messages, empty if none is available
   * @throws IllegalArgumentException if {@code claimerId} is blank, {@code kinds} is empty, or
   *     {@code maxMessages} is below 1
   */
  List<InboxMessage> claim(
      int partitionId,
      String claimerId,
      Set<MessageKind> kinds,
      int maxMessages,
      Duration claimTimeout);

  /** Hands out messages of every kind. See {@link #claim(int, String, Set, int, Duration)}. */
  default List<InboxMessage> claim(
      final int partitionId,
      final String claimerId,
      final int maxMessages,
      final Duration claimTimeout) {
    return claim(
        partitionId, claimerId, EnumSet.allOf(MessageKind.class), maxMessages, claimTimeout);
  }

  /**
   * Removes messages that the claimer finished processing.
   *
   * <p>Only messages on which the given claimer currently holds the claim are removed. If the claim
   * timed out and another claimer took the message, or the claim was released, the message is kept.
   * This makes a late acknowledgement from a node that is no longer the leader harmless.
   *
   * @param partitionId the partition of the messages
   * @param claimerId the claimer that holds the claim
   * @param sequences the sequences of the messages to remove
   * @return how many messages were removed
   */
  int acknowledge(int partitionId, String claimerId, Collection<Long> sequences);

  /**
   * Gives up all claims that the claimer holds on a partition, so that another claimer can take the
   * messages immediately. A new leader uses this for the claims of its predecessor.
   *
   * @param partitionId the partition
   * @param claimerId the claimer whose claims are released
   * @return how many messages were released
   */
  int releaseClaims(int partitionId, String claimerId);

  // ---- Gateway outbox ----

  /**
   * Stores the response to a request, for the gateway to collect.
   *
   * @param response the response
   * @return true if it was stored, false if a response for the same gateway and request id already
   *     exists. The first response is kept.
   */
  boolean putResponse(OutboxResponse response);

  /**
   * Returns and removes the responses that are ready for a gateway. Requests without a response are
   * not part of the result.
   *
   * @param gatewayId the gateway that collects
   * @param requestIds the request ids that the gateway waits for
   * @return the responses that exist, in any order
   */
  List<OutboxResponse> takeResponses(GatewayId gatewayId, Set<Long> requestIds);

  // ---- Partition leaders ----

  /**
   * Records the leader of a partition. The leader calls it when it starts to lead.
   *
   * @param leader the leader
   * @return true if the record was stored, false if the store holds a record with a higher term for
   *     the partition. A record with the same or a lower term is replaced.
   */
  boolean putLeader(PartitionLeader leader);

  /**
   * Removes the record of a leader, for example because it stopped leading. The record stays if it
   * belongs to another node or has another term, so a late call of a former leader is harmless.
   *
   * @param partitionId the partition
   * @param nodeId the node that led it
   * @param term the term in which it led
   * @return true if the record was removed
   */
  boolean removeLeader(int partitionId, String nodeId, long term);

  /**
   * Returns the recorded leader of a partition. The record is the last that a leader stored. A
   * leader that failed does not remove it, so the record shows where a leader was, not that it
   * still is there.
   *
   * @param partitionId the partition
   * @return the leader, or empty if none is recorded
   */
  Optional<PartitionLeader> findLeader(int partitionId);

  /**
   * Returns the recorded leaders of all partitions, one record per partition, with the same meaning
   * as {@link #findLeader(int)}. A gateway reads them all with one call to keep a copy.
   *
   * @return the leaders, in any order
   */
  List<PartitionLeader> findLeaders();

  // ---- Cleanup ----

  /**
   * Removes inbox messages and outbox responses that are older than the given times, whether or not
   * they are claimed.
   *
   * @param inboxTtl the age after which an inbox message is removed
   * @param outboxTtl the age after which an outbox response is removed
   * @return how many rows were removed
   */
  CleanupResult deleteExpired(Duration inboxTtl, Duration outboxTtl);
}
