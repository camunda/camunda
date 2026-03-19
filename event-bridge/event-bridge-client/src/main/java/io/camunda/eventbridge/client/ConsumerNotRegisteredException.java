/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

/** Thrown when the coordinator reports that a consumer is not registered (heartbeat timeout). */
public final class ConsumerNotRegisteredException extends EventBridgeException {

  public ConsumerNotRegisteredException(final String groupId, final String consumerId) {
    super(
        "Consumer not registered: groupId="
            + groupId
            + ", consumerId="
            + consumerId
            + ". Call subscribe() to rejoin.");
  }
}
