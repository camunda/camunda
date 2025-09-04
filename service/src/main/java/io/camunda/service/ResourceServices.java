/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeleteResourceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeployResourceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerFetchResourceRequest;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ResourceServices extends ApiServices<ResourceServices> {

  public ResourceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider) {
    super(brokerClient, securityContextProvider, authentication, executorProvider);
  }

  @Override
  public ResourceServices withAuthentication(final CamundaAuthentication authentication) {
    return new ResourceServices(
        brokerClient, securityContextProvider, authentication, executorProvider);
  }

  public CompletableFuture<DeploymentRecord> deployResources(
      final DeployResourcesRequest deployResourcesRequest) {
    final var brokerRequest = new BrokerDeployResourceRequest();
    deployResourcesRequest.resources().forEach(brokerRequest::addResource);
    brokerRequest.setTenantId(deployResourcesRequest.tenantId());
    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<ResourceDeletionRecord> deleteResource(
      final ResourceDeletionRequest request) {
    final var brokerRequest =
        new BrokerDeleteResourceRequest().setResourceKey(request.resourceKey());
    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<ResourceRecord> fetchResource(final ResourceFetchRequest request) {
    return sendBrokerRequest(
        new BrokerFetchResourceRequest().setResourceKey(request.resourceKey()));
  }

  public record DeployResourcesRequest(Map<String, byte[]> resources, String tenantId) {}

  public record ResourceDeletionRequest(long resourceKey, Long operationReference) {}

  public record ResourceFetchRequest(long resourceKey) {}
}
