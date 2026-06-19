/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerClusterVersionRaiseRequest;
import io.camunda.zeebe.protocol.impl.record.value.clusterversion.ClusterVersionRecord;
import java.util.concurrent.CompletableFuture;

/**
 * Cluster-wide service for raising the Engine Capability Version. ECV is a global setting — not
 * tenant-scoped — so this class deliberately does not extend {@link PhysicalTenantScopedApiServices}
 * and has no authentication/authorization plumbing in the PoC.
 */
public final class ClusterVersionServices {

  private final BrokerClient brokerClient;

  public ClusterVersionServices(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
  }

  public CompletableFuture<ClusterVersionRecord> raise(final int line, final int ordinal) {
    return brokerClient
        .sendRequest(new BrokerClusterVersionRaiseRequest(line, ordinal))
        .thenApply(response -> response.getResponse());
  }
}
