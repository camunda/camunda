/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker;

import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.RequestDispatchStrategy;

/**
 * Dispatch strategy for message publish and correlate requests. Delegates to {@link
 * HashBasedDispatchStrategy} using the correlation key to deterministically route messages to the
 * same partition.
 */
public final class PublishMessageDispatchStrategy implements RequestDispatchStrategy {

  private final HashBasedDispatchStrategy delegate;

  public PublishMessageDispatchStrategy(final String correlationKey) {
    delegate = new HashBasedDispatchStrategy(correlationKey, "correlation key");
  }

  @Override
  public int determinePartition(final BrokerTopologyManager topologyManager) {
    return delegate.determinePartition(topologyManager);
  }
}
