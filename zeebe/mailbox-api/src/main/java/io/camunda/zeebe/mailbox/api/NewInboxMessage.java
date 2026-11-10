/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api;

/**
 * A message that a sender wants to put into the inbox of a partition.
 *
 * <p>The key {@code (partitionId, sender, requestId)} identifies the message. Inserting the same
 * key a second time has no effect, so that a sender can retry an insert safely.
 *
 * <p>The payload is treated as immutable. Callers must not change the array after they pass it.
 *
 * @param partitionId the partition that must process the message
 * @param kind the kind of the message
 * @param sender who sent it, for example a gateway id or a partition id. Not blank.
 * @param requestId an identifier that is unique for the sender
 * @param payload the serialized message
 */
public record NewInboxMessage(
    int partitionId, MessageKind kind, String sender, long requestId, byte[] payload) {

  public NewInboxMessage {
    if (sender.isBlank()) {
      throw new IllegalArgumentException("sender must not be blank");
    }
  }
}
