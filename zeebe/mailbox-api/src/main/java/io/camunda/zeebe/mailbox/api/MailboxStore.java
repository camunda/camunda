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
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Storage of the cross-region mailbox. It holds a {@code partition inbox} for each partition, a
 * {@code gateway outbox} for responses, and a registry that maps gateways to numeric stream ids.
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
   * @param partitionId the partition whose inbox is read
   * @param claimerId identifies the caller, for example the node that leads the partition. Not
   *     blank.
   * @param maxMessages the most messages to return, at least 1
   * @param claimTimeout how long the claim stays valid
   * @return the claimed messages, empty if none is available
   */
  List<InboxMessage> claim(
      int partitionId, String claimerId, int maxMessages, Duration claimTimeout);

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

  // ---- Gateway registry ----

  /**
   * Returns the numeric stream id of a gateway and creates it if needed. The same gateway always
   * receives the same id, and different gateways receive different ids. A broker stores this id in
   * the records of a request, and later finds the gateway with {@link #findGateway(int)}.
   *
   * @param gatewayId the gateway
   * @return its stream id
   */
  int registerGateway(GatewayId gatewayId);

  /**
   * @param streamId a stream id that {@link #registerGateway(GatewayId)} returned
   * @return the gateway, or empty if the id is unknown
   */
  Optional<GatewayId> findGateway(int streamId);

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
