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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.clients.ProcessDefinitionSearchClient;
import io.camunda.search.clients.ResourceSearchClient;
import io.camunda.search.entities.ResourceEntity;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeleteResourceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeployResourceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerFetchResourceRequest;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.value.ResourceType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ResourceServices extends ApiServices<ResourceServices> {

  private final ProcessDefinitionSearchClient processDefinitionSearchClient;
  private final DecisionRequirementSearchClient decisionRequirementSearchClient;
  private final ResourceSearchClient resourceSearchClient;
  private final boolean secondaryStorageEnabled;
  private final Cache<Long, ResourceEntity> resourceCache;

  public ResourceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final ProcessDefinitionSearchClient processDefinitionSearchClient,
      final DecisionRequirementSearchClient decisionRequirementSearchClient,
      final ResourceSearchClient resourceSearchClient,
      final boolean secondaryStorageEnabled,
      final long resourceCacheMaxSize) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.processDefinitionSearchClient = processDefinitionSearchClient;
    this.decisionRequirementSearchClient = decisionRequirementSearchClient;
    this.resourceSearchClient = resourceSearchClient;
    this.secondaryStorageEnabled = secondaryStorageEnabled;
    resourceCache = Caffeine.newBuilder().maximumSize(resourceCacheMaxSize).build();
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
    return sendBrokerRequest(brokerRequest, authentication)
        .whenComplete((result, ex) -> resourceCache.invalidate(request.resourceKey()));
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

  /**
   * Fetches a resource by key from primary storage (broker). Used for content retrieval.
   *
   * @param request the resource fetch request
   * @param authentication the authentication context
   * @return a future with the resource record
   */
  public CompletableFuture<ResourceRecord> fetchResource(
      final ResourceFetchRequest request, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerFetchResourceRequest().setResourceKey(request.resourceKey()), authentication);
  }

  /**
   * Gets resource metadata by key. Fetches from secondary storage (search) if available, with
   * fallback to primary storage (broker) when running without secondary storage. Results are cached
   * to avoid repeated lookups.
   *
   * @param resourceKey the resource key
   * @param authentication the authentication context
   * @return a future with the resource entity
   */
  public CompletableFuture<ResourceEntity> getByKey(
      final long resourceKey, final CamundaAuthentication authentication) {
    final var cached = resourceCache.getIfPresent(resourceKey);
    if (cached != null) {
      return CompletableFuture.completedFuture(cached);
    }
    if (secondaryStorageEnabled) {
      final var securityContext = securityContextProvider.provideSecurityContext(authentication);
      final var entity =
          resourceSearchClient.withSecurityContext(securityContext).getResource(resourceKey);
      if (entity != null) {
        resourceCache.put(resourceKey, entity);
      }
      return CompletableFuture.completedFuture(entity);
    } else {
      // Fall back to primary storage when running without secondary storage
      return fetchResource(new ResourceFetchRequest(resourceKey), authentication)
          .thenApply(
              record ->
                  new ResourceEntity(
                      record.getResourceKey(),
                      record.getResourceId(),
                      record.getResourceName(),
                      record.getVersion(),
                      record.getVersionTag(),
                      record.getDeploymentKey(),
                      record.getTenantId()));
    }
  }

  public record DeployResourcesRequest(Map<String, byte[]> resources, String tenantId) {}

  public record ResourceDeletionRequest(
      long resourceKey, Long operationReference, boolean deleteHistory) {}

  public record ResourceFetchRequest(long resourceKey) {}
}

