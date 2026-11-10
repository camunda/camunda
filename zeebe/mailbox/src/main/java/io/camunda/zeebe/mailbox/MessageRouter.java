/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import io.camunda.zeebe.mailbox.api.PartitionReachability;

/** Chooses the path of a message: the network if the leader is reachable, else the mailbox. */
public final class MessageRouter {

  /** The path that a message takes. */
  public enum Route {
    NETWORK,
    MAILBOX
  }

  private final PartitionReachability reachability;

  public MessageRouter(final PartitionReachability reachability) {
    this.reachability = reachability;
  }

  public Route route(final int partitionId) {
    return reachability.isReachable(partitionId) ? Route.NETWORK : Route.MAILBOX;
  }
}
