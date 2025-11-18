/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

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
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import java.util.concurrent.CompletableFuture;
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
      final String name, final Object value) {
    return sendBrokerRequest(
        new BrokerCreateClusterVariableRequest()
            .setName(name)
            .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value)))
            .setGlobalScope());
  }

  public CompletableFuture<ClusterVariableRecord> createTenantScopedClusterVariable(
      final String name, final Object value, final String tenantId) {
    return sendBrokerRequest(
        new BrokerCreateClusterVariableRequest()
            .setName(name)
            .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value)))
            .setTenantScope(tenantId));
  }

  public CompletableFuture<ClusterVariableRecord> deleteGloballyScopedClusterVariable(
      final String name) {
    return sendBrokerRequest(
        new BrokerDeleteClusterVariableRequest().setName(name).setGlobalScope());
  }

  public CompletableFuture<ClusterVariableRecord> deleteTenantScopedClusterVariable(
      final String name, final String tenantId) {
    return sendBrokerRequest(
        new BrokerDeleteClusterVariableRequest().setName(name).setTenantScope(tenantId));
  }

  public ClusterVariableEntity getGloballyScopedClusterVariable(final String name) {
    return executeSearchRequest(
        () ->
            clusterVariableSearchClient
                // TODO : Uncomment and fix authorization once implemented
                /*                .withSecurityContext(
                securityContextProvider.provideSecurityContext(
                    authentication,
                    withAuthorization())*/
                .getClusterVariable(name));
  }

  public ClusterVariableEntity getTenantScopedClusterVariable(
      final String name, final String tenantId) {
    return executeSearchRequest(
        () ->
            clusterVariableSearchClient
                // TODO : Uncomment and fix authorization once implemented
                /*                .withSecurityContext(
                securityContextProvider.provideSecurityContext(
                    authentication,
                    withAuthorization()))*/
                .getClusterVariable(name, tenantId));
  }

  @Override
  public SearchQueryResult<ClusterVariableEntity> search(final ClusterVariableQuery query) {
    return null;
  }
}
