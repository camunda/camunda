/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.hub;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;

/**
 * Sends policy update commands to partition 1 of a physical tenant with the {@link BrokerClient}
 * (ADR 0015, D3). The broker does not split updates yet, so an update is sent as its single chunk.
 */
@NullMarked
public final class BrokerClientPolicyInPort implements PolicyInPort {

  private final String physicalTenantId;
  private final BrokerClient brokerClient;

  public BrokerClientPolicyInPort(final String physicalTenantId, final BrokerClient brokerClient) {
    this.physicalTenantId = physicalTenantId;
    this.brokerClient = brokerClient;
  }

  @Override
  public CompletableFuture<Void> apply(final PolicyUpdateRecord update) {
    return send(new BrokerPolicyUpdateRequest(update)).thenAccept(appended -> {});
  }

  @Override
  public CompletableFuture<PolicyStatus> queryStatus() {
    return send(new BrokerPolicyStatusRequest()).thenApply(PolicyStatus::of);
  }

  /** Sends the request; the future fails on a rejection or an error. */
  private <T> CompletableFuture<T> send(final BrokerExecuteCommand<T> request) {
    request.setPartitionGroup(physicalTenantId);
    return brokerClient
        .sendRequestWithRetry(request)
        .thenCompose(
            (BrokerResponse<T> response) ->
                response.isResponse()
                    ? CompletableFuture.completedFuture(response.getResponseOrThrow())
                    : CompletableFuture.failedFuture(response.toException()));
  }
}
