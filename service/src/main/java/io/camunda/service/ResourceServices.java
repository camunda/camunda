/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.decisionRequirementsSearchQuery;
import static io.camunda.search.query.SearchQueryBuilders.processDefinitionSearchQuery;

import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.clients.DeployedResourceSearchClient;
import io.camunda.search.clients.ProcessDefinitionSearchClient;
import io.camunda.search.entities.DeployedResourceEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeleteResourceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeployResourceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerFetchResourceRequest;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.value.ResourceType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ResourceServices extends ApiServices<ResourceServices> {

  private final ProcessDefinitionSearchClient processDefinitionSearchClient;
  private final DecisionRequirementSearchClient decisionRequirementSearchClient;
  private final DeployedResourceSearchClient deployedResourceSearchClient;
  private final boolean secondaryStorageEnabled;

  public ResourceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final ProcessDefinitionSearchClient processDefinitionSearchClient,
      final DecisionRequirementSearchClient decisionRequirementSearchClient,
      final DeployedResourceSearchClient deployedResourceSearchClient,
      final boolean secondaryStorageEnabled) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.processDefinitionSearchClient = processDefinitionSearchClient;
    this.decisionRequirementSearchClient = decisionRequirementSearchClient;
    this.deployedResourceSearchClient = deployedResourceSearchClient;
    this.secondaryStorageEnabled = secondaryStorageEnabled;
  }

  public CompletableFuture<DeploymentRecord> deployResources(
      final DeployResourcesRequest deployResourcesRequest,
      final CamundaAuthentication authentication) {
    final var brokerRequest = new BrokerDeployResourceRequest();
    deployResourcesRequest.resources().forEach(brokerRequest::addResource);
    brokerRequest.setTenantId(deployResourcesRequest.tenantId());
    return sendBrokerRequest(brokerRequest, authentication);
  }

  public CompletableFuture<ResourceDeletionRecord> deleteResource(
      final ResourceDeletionRequest request, final CamundaAuthentication authentication) {
    final var brokerRequest =
        new BrokerDeleteResourceRequest()
            .setResourceKey(request.resourceKey())
            .setDeleteHistory(request.deleteHistory());

    enrichResourceDeletionRecordWithHistoryDeletionData(request, brokerRequest);

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest, authentication);
  }

  /**
   * It could happen that a resource is deleted from primary storage, without the historic data
   * being deleted from secondary storage. When this happens, we still want this broker request to
   * be able to delete the historic data. Unfortunately, we cannot rely on the resource key to find
   * the data we need for the authorization checks and the actual deletion. Since the resource must
   * then be in secondary storage, we retrieve the necessary data here and pass it along the broker.
   */
  private void enrichResourceDeletionRecordWithHistoryDeletionData(
      final ResourceDeletionRequest request, final BrokerDeleteResourceRequest brokerRequest) {
    if (request.deleteHistory()) {
      final var processDefinition =
          processDefinitionSearchClient
              .withSecurityContext(
                  securityContextProvider.provideSecurityContext(CamundaAuthentication.anonymous()))
              .searchProcessDefinitions(
                  processDefinitionSearchQuery()
                      .filter(f -> f.processDefinitionKeys(request.resourceKey()))
                      .build())
              .items();
      if (!processDefinition.isEmpty()) {
        brokerRequest
            .setResourceType(ResourceType.PROCESS_DEFINITION)
            .setResourceId(processDefinition.getFirst().processDefinitionId())
            .setTenantId(processDefinition.getFirst().tenantId());
      } else {
        final var decisionRequirements =
            decisionRequirementSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        CamundaAuthentication.anonymous()))
                .searchDecisionRequirements(
                    decisionRequirementsSearchQuery()
                        .filter(f -> f.decisionRequirementsKeys(request.resourceKey()))
                        .build())
                .items();
        if (!decisionRequirements.isEmpty()) {
          brokerRequest
              .setResourceType(ResourceType.DECISION_REQUIREMENTS)
              .setResourceId(decisionRequirements.getFirst().decisionRequirementsId())
              .setTenantId(decisionRequirements.getFirst().tenantId());
        }
      }
    }
  }

  public CompletableFuture<DeployedResourceEntity> getByKey(
      final long resourceKey, final CamundaAuthentication authentication) {
    return fetchDeployedResource(resourceKey, authentication, false);
  }

  public CompletableFuture<DeployedResourceEntity> getContentByKey(
      final long resourceKey, final CamundaAuthentication authentication) {
    return fetchDeployedResource(resourceKey, authentication, true);
  }

  private CompletableFuture<DeployedResourceEntity> fetchDeployedResource(
      final long resourceKey,
      final CamundaAuthentication authentication,
      final boolean includeContent) {
    if (secondaryStorageEnabled) {
      final var securityContext = securityContextProvider.provideSecurityContext(authentication);
      return CompletableFuture.supplyAsync(
          () -> {
            try {
              final var client = deployedResourceSearchClient.withSecurityContext(securityContext);
              return includeContent
                  ? client.getDeployedResource(resourceKey)
                  : client.getDeployedResourceMetadata(resourceKey);
            } catch (final CamundaSearchException cse) {
              throw ErrorMapper.mapSearchError(cse);
            }
          },
          executorProvider.getExecutor());
    } else {
      return sendBrokerRequest(
              new BrokerFetchResourceRequest().setResourceKey(resourceKey), authentication)
          .thenApply(
              record ->
                  new DeployedResourceEntity(
                      record.getResourceKey(),
                      record.getResourceId(),
                      record.getResourceName(),
                      record.getVersion(),
                      record.getVersionTag(),
                      record.getDeploymentKey(),
                      record.getTenantId(),
                      includeContent ? record.getResourceProp() : null));
    }
  }

  public record DeployResourcesRequest(Map<String, byte[]> resources, String tenantId) {}

  public record ResourceDeletionRequest(
      long resourceKey, Long operationReference, boolean deleteHistory) {}
}
