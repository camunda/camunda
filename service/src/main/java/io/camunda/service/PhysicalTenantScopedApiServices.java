/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public abstract class PhysicalTenantScopedApiServices<T extends PhysicalTenantScopedApiServices<T>>
    extends ApiServices<T> {

  private final String physicalTenantId;

  protected PhysicalTenantScopedApiServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.physicalTenantId = physicalTenantId;
  }

  protected String getPhysicalTenantId() {
    return physicalTenantId;
  }

  @Override
  protected final List<BiConsumer<BrokerRequest, CamundaAuthentication>> brokerRequestMutators() {
    return Stream.concat(
            super.brokerRequestMutators().stream(),
            Stream.of(
                (brokerRequest, ignored) -> brokerRequest.setPartitionGroup(physicalTenantId)))
        .toList();
  }
}
