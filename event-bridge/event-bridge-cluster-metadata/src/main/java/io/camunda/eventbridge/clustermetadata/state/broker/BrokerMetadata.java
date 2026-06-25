/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.broker;

/**
 * A broker's replicated registration: its node id, the monotonic {@code brokerEpoch} assigned at
 * registration (a stale epoch fences heartbeats from an earlier incarnation), the {@code
 * incarnation} the broker proposed, and its liveness {@link BrokerStatus}. The event-bridge
 * counterpart of {@code TopicMetadata}.
 */
public record BrokerMetadata(
    int brokerId, long brokerEpoch, BrokerStatus status, long incarnation) {

  /**
   * The broker's liveness state. Only {@link #ACTIVE} brokers are placement targets; {@link
   * #FENCED} (session lapsed) and {@link #DRAINING} (controlled shutdown) brokers are excluded and
   * their replicas are moved off.
   */
  public enum BrokerStatus {
    ACTIVE,
    FENCED,
    DRAINING
  }
}
