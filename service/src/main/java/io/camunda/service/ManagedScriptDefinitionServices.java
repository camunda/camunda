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
import io.camunda.service.exception.ServiceException;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateManagedScriptDefinitionRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerGetManagedScriptDefinitionRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerRenewManagedScriptDefinitionLeaseRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUpdateManagedScriptDefinitionRequest;
import io.camunda.zeebe.protocol.impl.record.value.managedscriptdefinition.ManagedScriptDefinitionRecord;
import io.camunda.zeebe.protocol.record.value.ManagedScriptDefinitionStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ManagedScriptDefinitionServices
    extends PhysicalTenantScopedApiServices<ManagedScriptDefinitionServices> {

  public ManagedScriptDefinitionServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  public CompletableFuture<List<ManagedScriptDefinitionRecord>> activate(
      final String provider,
      final String worker,
      final long leaseDuration,
      final int maxDefinitions,
      final String tenantId,
      final CamundaAuthentication authentication) {
    if (provider == null || provider.isBlank()) {
      throw new ServiceException(
          "The provider must not be blank", ServiceException.Status.INVALID_ARGUMENT);
    }
    if (worker == null || worker.isBlank()) {
      throw new ServiceException(
          "The worker must not be blank", ServiceException.Status.INVALID_ARGUMENT);
    }
    if (leaseDuration <= 0) {
      throw new ServiceException(
          "The lease duration must be greater than zero", ServiceException.Status.INVALID_ARGUMENT);
    }
    if (maxDefinitions <= 0) {
      throw new ServiceException(
          "The maximum number of definitions must be greater than zero",
          ServiceException.Status.INVALID_ARGUMENT);
    }

    final var partitions = brokerClient.getTopologyManager().getTopology().getPartitions();
    if (partitions.isEmpty()) {
      return CompletableFuture.failedFuture(
          new ServiceException(
              "Cannot activate managed scripts because no partitions are known",
              ServiceException.Status.UNAVAILABLE));
    }
    return activateUntilLimit(
        provider,
        worker,
        leaseDuration,
        maxDefinitions,
        tenantId,
        authentication,
        partitions,
        new ArrayList<>(),
        0,
        false);
  }

  public CompletableFuture<ManagedScriptDefinitionRecord> renewLease(
      final long definitionKey,
      final long revision,
      final String leaseToken,
      final long leaseDuration,
      final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerRenewManagedScriptDefinitionLeaseRequest(
            definitionKey, revision, leaseToken, leaseDuration),
        authentication);
  }

  public CompletableFuture<ManagedScriptDefinitionRecord> update(
      final BrokerUpdateManagedScriptDefinitionRequest request,
      final CamundaAuthentication authentication) {
    return sendBrokerRequest(request, authentication);
  }

  public CompletableFuture<ManagedScriptDefinitionRecord> checkpointDeployment(
      final long definitionKey,
      final long revision,
      final String leaseToken,
      final String operationId,
      final String providerOperationId,
      final CamundaAuthentication authentication) {
    return update(
        new BrokerUpdateManagedScriptDefinitionRequest(
                definitionKey,
                revision,
                leaseToken,
                operationId,
                ManagedScriptDefinitionStatus.DEPLOYING)
            .setProviderOperationId(providerOperationId),
        authentication);
  }

  public CompletableFuture<ManagedScriptDefinitionRecord> completeDeployment(
      final long definitionKey,
      final long revision,
      final String leaseToken,
      final String operationId,
      final String providerDeploymentId,
      final CamundaAuthentication authentication) {
    return update(
        new BrokerUpdateManagedScriptDefinitionRequest(
                definitionKey,
                revision,
                leaseToken,
                operationId,
                ManagedScriptDefinitionStatus.READY)
            .setProviderDeploymentId(providerDeploymentId),
        authentication);
  }

  public CompletableFuture<ManagedScriptDefinitionRecord> failDeployment(
      final long definitionKey,
      final long revision,
      final String leaseToken,
      final String operationId,
      final String code,
      final String message,
      final boolean retryable,
      final CamundaAuthentication authentication) {
    return update(
        new BrokerUpdateManagedScriptDefinitionRequest(
                definitionKey,
                revision,
                leaseToken,
                operationId,
                ManagedScriptDefinitionStatus.FAILED)
            .setFailure(code, message, retryable),
        authentication);
  }

  public CompletableFuture<ManagedScriptDefinitionRecord> get(
      final long definitionKey, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerGetManagedScriptDefinitionRequest(definitionKey), authentication);
  }

  public CompletableFuture<ManagedScriptDefinitionRecord> get(
      final long processDefinitionKey,
      final String elementId,
      final CamundaAuthentication authentication) {
    if (processDefinitionKey <= 0) {
      throw new ServiceException(
          "The process definition key must be greater than zero",
          ServiceException.Status.INVALID_ARGUMENT);
    }
    if (elementId == null || elementId.isBlank()) {
      throw new ServiceException(
          "The element ID must not be blank", ServiceException.Status.INVALID_ARGUMENT);
    }
    return sendBrokerRequest(
        new BrokerGetManagedScriptDefinitionRequest(processDefinitionKey, elementId),
        authentication);
  }

  private CompletableFuture<List<ManagedScriptDefinitionRecord>> activateUntilLimit(
      final String provider,
      final String worker,
      final long leaseDuration,
      final int maxDefinitions,
      final String tenantId,
      final CamundaAuthentication authentication,
      final List<Integer> partitions,
      final List<ManagedScriptDefinitionRecord> activated,
      final int partitionIndex,
      final boolean foundInPass) {
    if (activated.size() >= maxDefinitions) {
      return CompletableFuture.completedFuture(List.copyOf(activated));
    }
    if (partitionIndex >= partitions.size()) {
      if (!foundInPass) {
        return CompletableFuture.completedFuture(List.copyOf(activated));
      }
      return activateUntilLimit(
          provider,
          worker,
          leaseDuration,
          maxDefinitions,
          tenantId,
          authentication,
          partitions,
          activated,
          0,
          false);
    }

    final var request =
        new BrokerActivateManagedScriptDefinitionRequest(provider, worker, leaseDuration, tenantId);
    request.setPartitionId(partitions.get(partitionIndex));
    return sendBrokerRequest(request, authentication)
        .thenCompose(
            definition -> {
              final var activatedOnPartition = definition.getManagedScriptDefinitionKey() >= 0;
              if (activatedOnPartition) {
                activated.add(definition);
              }
              return activateUntilLimit(
                  provider,
                  worker,
                  leaseDuration,
                  maxDefinitions,
                  tenantId,
                  authentication,
                  partitions,
                  activated,
                  partitionIndex + 1,
                  foundInPass || activatedOnPartition);
            });
  }
}
