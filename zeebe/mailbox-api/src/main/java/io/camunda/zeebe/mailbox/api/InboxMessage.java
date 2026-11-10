/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api;

/**
 * A message that a claimer received from a partition inbox.
 *
 * <p>The payload is treated as immutable. Callers must not change the array.
 *
 * @param sequence an increasing number that the store assigned on insert. Messages of one partition
 *     are delivered in the order of their sequence.
 * @param partitionId the partition that must process the message
 * @param kind the kind of the message
 * @param sender who sent it
 * @param requestId the identifier that the sender gave it
 * @param payload the serialized message
 * @param deliveryCount how often the store has handed this message out, including this time. A
 *     value above 1 means that an earlier claimer did not acknowledge it, so it may already be
 *     processed.
 */
public record InboxMessage(
    long sequence,
    int partitionId,
    MessageKind kind,
    String sender,
    long requestId,
    byte[] payload,
    int deliveryCount) {}
