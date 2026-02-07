/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.CLUSTER_VARIABLE_READ_AUTHORIZATION;

import io.camunda.search.clients.ClusterVariableSearchClient;
import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateClusterVariableRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeleteClusterVariableRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUpdateClusterVariableRequest;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import java.util.concurrent.CompletableFuture;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ClusterVariableServices
    extends SearchQueryService<
        ClusterVariableServices, ClusterVariableQuery, ClusterVariableEntity> {

  private final ClusterVariableSearchClient clusterVariableSearchClient;

  public ClusterVariableServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ClusterVariableSearchClient clusterVariableSearchClient,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.clusterVariableSearchClient = clusterVariableSearchClient;
  }

  @Override
  public ClusterVariableServices withAuthentication(final CamundaAuthentication authentication) {
    return new ClusterVariableServices(
        brokerClient,
        securityContextProvider,
        clusterVariableSearchClient,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  public CompletableFuture<ClusterVariableRecord> createGloballyScopedClusterVariable(
      final ClusterVariableRequest request) {
    return sendBrokerRequest(
        new BrokerCreateClusterVariableRequest()
            .setName(request.name())
            .setValue(toDirectBufferValue(request.value()))
            .setGlobalScope());
  }

  public CompletableFuture<ClusterVariableRecord> createTenantScopedClusterVariable(
      final ClusterVariableRequest request) {
    return sendBrokerRequest(
        new BrokerCreateClusterVariableRequest()
            .setName(request.name())
            .setValue(toDirectBufferValue(request.value()))
            .setTenantScope(request.tenantId()));
  }

  public CompletableFuture<ClusterVariableRecord> deleteGloballyScopedClusterVariable(
      final ClusterVariableRequest request) {
    return sendBrokerRequest(
        new BrokerDeleteClusterVariableRequest().setName(request.name()).setGlobalScope());
  }

  public CompletableFuture<ClusterVariableRecord> deleteTenantScopedClusterVariable(
      final ClusterVariableRequest request) {
    return sendBrokerRequest(
        new BrokerDeleteClusterVariableRequest()
            .setName(request.name())
            .setTenantScope(request.tenantId()));
  }

  public CompletableFuture<ClusterVariableRecord> updateGloballyScopedClusterVariable(
      final ClusterVariableRequest request) {
    return sendBrokerRequest(
        new BrokerUpdateClusterVariableRequest()
            .setName(request.name())
            .setValue(toDirectBufferValue(request.value()))
            .setGlobalScope());
  }

  public CompletableFuture<ClusterVariableRecord> updateTenantScopedClusterVariable(
      final ClusterVariableRequest request) {
    return sendBrokerRequest(
        new BrokerUpdateClusterVariableRequest()
            .setName(request.name())
            .setValue(toDirectBufferValue(request.value()))
            .setTenantScope(request.tenantId()));
  }

  public ClusterVariableEntity getGloballyScopedClusterVariable(
      final ClusterVariableRequest request) {
    return executeSearchRequest(
        () ->
            clusterVariableSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withAuthorization(
                            CLUSTER_VARIABLE_READ_AUTHORIZATION, ClusterVariableEntity::name)))
                .getClusterVariable(request.name()));
  }

  public ClusterVariableEntity getTenantScopedClusterVariable(
      final ClusterVariableRequest request) {
    return executeSearchRequest(
        () ->
            clusterVariableSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withAuthorization(
                            CLUSTER_VARIABLE_READ_AUTHORIZATION, ClusterVariableEntity::name)))
                .getClusterVariable(request.name(), request.tenantId()));
  }

  @Override
  public SearchQueryResult<ClusterVariableEntity> search(final ClusterVariableQuery query) {
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

  public record ClusterVariableRequest(String name, Object value, String tenantId) {}
}
