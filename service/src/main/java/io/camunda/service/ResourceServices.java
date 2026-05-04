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
import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.RESOURCE_READ_AUTHORIZATION;

import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.clients.DeployedResourceSearchClient;
import io.camunda.search.clients.ProcessDefinitionSearchClient;
import io.camunda.search.entities.DeployedResourceEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.query.DeployedResourceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.util.ResourceUtils;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.exception.ServiceException;
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
    return fetchDeployedResource(resourceKey, authentication, false, null);
  }

  public CompletableFuture<DeployedResourceEntity> getContentByKey(
      final long resourceKey, final CamundaAuthentication authentication) {
    return fetchDeployedResource(resourceKey, authentication, true, null);
  }

  public CompletableFuture<DeployedResourceEntity> getContentByKeyFilteredByType(
      final long resourceKey,
      final String resourceType,
      final CamundaAuthentication authentication) {
    return fetchDeployedResource(resourceKey, authentication, true, resourceType);
  }

  public SearchQueryResult<DeployedResourceEntity> search(
      final DeployedResourceQuery query, final CamundaAuthentication authentication) {
    try {
      return deployedResourceSearchClient
          .withSecurityContext(
              securityContextProvider.provideSecurityContext(
                  authentication, RESOURCE_READ_AUTHORIZATION))
          .searchDeployedResources(query);
    } catch (final CamundaSearchException e) {
      throw ErrorMapper.mapSearchError(e);
    }
  }

  private CompletableFuture<DeployedResourceEntity> fetchDeployedResource(
      final long resourceKey,
      final CamundaAuthentication authentication,
      final boolean includeContent,
      final String resourceTypeFilter) {
    if (secondaryStorageEnabled) {
      return fetchFromSecondaryStorage(
          resourceKey, authentication, includeContent, resourceTypeFilter);
    }
    return fetchFromBroker(resourceKey, authentication, includeContent, resourceTypeFilter);
  }

  private CompletableFuture<DeployedResourceEntity> fetchFromSecondaryStorage(
      final long resourceKey,
      final CamundaAuthentication authentication,
      final boolean includeContent,
      final String resourceTypeFilter) {
    final var securityContext =
        securityContextProvider.provideSecurityContext(
            authentication,
            withAuthorization(RESOURCE_READ_AUTHORIZATION, DeployedResourceEntity::resourceId));
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            final var client = deployedResourceSearchClient.withSecurityContext(securityContext);
            final DeployedResourceEntity entity =
                includeContent
                    ? client.getDeployedResource(resourceKey)
                    : client.getDeployedResourceMetadata(resourceKey);
            validateResourceType(entity.resourceType(), resourceTypeFilter, resourceKey);
            return entity;
          } catch (final CamundaSearchException cse) {
            throw ErrorMapper.mapSearchError(cse);
          }
        },
        executorProvider.getExecutor());
  }

  private CompletableFuture<DeployedResourceEntity> fetchFromBroker(
      final long resourceKey,
      final CamundaAuthentication authentication,
      final boolean includeContent,
      final String resourceTypeFilter) {
    return sendBrokerRequest(
            new BrokerFetchResourceRequest().setResourceKey(resourceKey), authentication)
        .handle(
            (record, error) -> {
              if (error != null) {
                // Normalize error message to match secondary storage format
                throw mapResourceNotFoundError(error, resourceKey);
              }
              final DeployedResourceEntity entity =
                  new DeployedResourceEntity(
                      record.getResourceKey(),
                      record.getResourceId(),
                      record.getResourceName(),
                      ResourceUtils.deriveResourceType(record.getResourceName()),
                      record.getVersion(),
                      record.getVersionTag(),
                      record.getDeploymentKey(),
                      record.getTenantId(),
                      includeContent ? record.getResourceProp() : null);
              validateResourceType(entity.resourceType(), resourceTypeFilter, resourceKey);
              return entity;
            });
  }

  private void validateResourceType(
      final String entityResourceType, final String resourceTypeFilter, final long resourceKey) {
    if (resourceTypeFilter != null && !resourceTypeFilter.equalsIgnoreCase(entityResourceType)) {
      throw resourceNotFoundException(resourceKey);
    }
  }

  private static ServiceException resourceNotFoundException(final long resourceKey) {
    return new ServiceException(
        String.format("Resource with key '%d' not found", resourceKey),
        ServiceException.Status.NOT_FOUND);
  }

  // Normalizes NOT_FOUND errors to match secondary storage format
  private ServiceException mapResourceNotFoundError(final Throwable error, final long resourceKey) {
    final ServiceException mappedException = ErrorMapper.mapError(error);
    if (mappedException.getStatus() == ServiceException.Status.NOT_FOUND) {
      return resourceNotFoundException(resourceKey);
    }
    return mappedException;
  }

  public record DeployResourcesRequest(Map<String, byte[]> resources, String tenantId) {}

  public record ResourceDeletionRequest(
      long resourceKey, Long operationReference, boolean deleteHistory) {}
}
