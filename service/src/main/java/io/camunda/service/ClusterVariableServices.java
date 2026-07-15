/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.core.auth.RequiredAuthorization.withRequiredAuthorization;
import static io.camunda.service.authorization.Authorizations.CLUSTER_VARIABLE_READ_AUTHORIZATION;

import io.camunda.search.clients.ClusterVariableSearchClient;
import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateClusterVariableRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeleteClusterVariableRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUpdateClusterVariableRequest;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ClusterVariableServices
    extends SearchQueryService<
        ClusterVariableServices, ClusterVariableQuery, ClusterVariableEntity> {

  private final ClusterVariableSearchClient clusterVariableSearchClient;

  public ClusterVariableServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ClusterVariableSearchClient clusterVariableSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.clusterVariableSearchClient = clusterVariableSearchClient;
  }

  public CompletableFuture<ClusterVariableRecord> createGloballyScopedClusterVariable(
      final ClusterVariableRequest request, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerCreateClusterVariableRequest()
            .setName(request.name())
            .setValue(toDirectBufferValue(request.value()))
            .setMetadata(toDirectBufferMetadata(request.metadata()))
            .setGlobalScope(),
        authentication);
  }

  public CompletableFuture<ClusterVariableRecord> createTenantScopedClusterVariable(
      final ClusterVariableRequest request, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerCreateClusterVariableRequest()
            .setName(request.name())
            .setValue(toDirectBufferValue(request.value()))
            .setMetadata(toDirectBufferMetadata(request.metadata()))
            .setTenantScope(request.tenantId()),
        authentication);
  }

  public CompletableFuture<ClusterVariableRecord> deleteGloballyScopedClusterVariable(
      final ClusterVariableRequest request, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerDeleteClusterVariableRequest().setName(request.name()).setGlobalScope(),
        authentication);
  }

  public CompletableFuture<ClusterVariableRecord> deleteTenantScopedClusterVariable(
      final ClusterVariableRequest request, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerDeleteClusterVariableRequest()
            .setName(request.name())
            .setTenantScope(request.tenantId()),
        authentication);
  }

  public CompletableFuture<ClusterVariableRecord> updateGloballyScopedClusterVariable(
      final ClusterVariableRequest request, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerUpdateClusterVariableRequest()
            .setName(request.name())
            .setValue(toDirectBufferValue(request.value()))
            .setMetadata(
                toDirectBufferMetadata(
                    resolveMetadataForUpdate(
                        request.metadata(),
                        () -> getGloballyScopedClusterVariable(request, authentication))))
            .setGlobalScope(),
        authentication);
  }

  public CompletableFuture<ClusterVariableRecord> updateTenantScopedClusterVariable(
      final ClusterVariableRequest request, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerUpdateClusterVariableRequest()
            .setName(request.name())
            .setValue(toDirectBufferValue(request.value()))
            .setMetadata(
                toDirectBufferMetadata(
                    resolveMetadataForUpdate(
                        request.metadata(),
                        () -> getTenantScopedClusterVariable(request, authentication))))
            .setTenantScope(request.tenantId()),
        authentication);
  }

  public ClusterVariableEntity getGloballyScopedClusterVariable(
      final ClusterVariableRequest request, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            clusterVariableSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withRequiredAuthorization(
                            CLUSTER_VARIABLE_READ_AUTHORIZATION, ClusterVariableEntity::name)))
                .getClusterVariable(request.name()));
  }

  public ClusterVariableEntity getTenantScopedClusterVariable(
      final ClusterVariableRequest request, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            clusterVariableSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withRequiredAuthorization(
                            CLUSTER_VARIABLE_READ_AUTHORIZATION, ClusterVariableEntity::name)))
                .getClusterVariable(request.name(), request.tenantId()));
  }

  @Override
  public SearchQueryResult<ClusterVariableEntity> search(
      final ClusterVariableQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            clusterVariableSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, CLUSTER_VARIABLE_READ_AUTHORIZATION))
                .searchClusterVariables(query));
  }

  private DirectBuffer toDirectBufferValue(final Object value) {
    return new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value));
  }

  private DirectBuffer toDirectBufferMetadata(final Map<String, Object> metadata) {
    return toDirectBufferValue(metadata != null ? metadata : Map.of());
  }

  /**
   * Cluster variable updates replace the whole stored record, so an omitted {@code metadata} field
   * would otherwise silently wipe out previously stored metadata. When the client didn't send
   * metadata, fall back to the currently stored metadata instead of an empty map; if it can't be
   * looked up (e.g. the variable doesn't exist), let the update proceed and fail through the usual
   * existence check in the engine.
   */
  private Map<String, Object> resolveMetadataForUpdate(
      final Map<String, Object> metadata, final Supplier<ClusterVariableEntity> currentEntity) {
    if (metadata != null) {
      return metadata;
    }
    try {
      return toMetadataMap(currentEntity.get());
    } catch (final RuntimeException e) {
      return Map.of();
    }
  }

  private static Map<String, Object> toMetadataMap(final ClusterVariableEntity entity) {
    final var metadata = entity.metadata();
    if (metadata == null || metadata.isEmpty()) {
      return Map.of();
    }
    final Map<String, Object> result = new LinkedHashMap<>();
    metadata.forEach(
        entry ->
            result.put(
                entry.key(), entry.valueNumber() != null ? entry.valueNumber() : entry.value()));
    return result;
  }

  public record ClusterVariableRequest(
      String name, Object value, String tenantId, Map<String, Object> metadata) {}
}
