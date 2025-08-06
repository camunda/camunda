/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.authorization.Authorizations.TENANT_READER_AUTHORIZATION;

import io.camunda.search.clients.TenantSearchClient;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.TenantMemberEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerTenantEntityRequest;
import io.camunda.zeebe.gateway.impl.broker.request.tenant.BrokerTenantCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.tenant.BrokerTenantDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.tenant.BrokerTenantUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class TenantServices extends SearchQueryService<TenantServices, TenantQuery, TenantEntity> {

  private final TenantSearchClient tenantSearchClient;

  public TenantServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final TenantSearchClient tenantSearchClient,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider) {
    super(brokerClient, securityContextProvider, authentication, executorProvider);
    this.tenantSearchClient = tenantSearchClient;
  }

  @Override
  public SearchQueryResult<TenantEntity> search(final TenantQuery query) {
    return executeSearchRequest(
        () ->
            tenantSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, TENANT_READER_AUTHORIZATION))
                .searchTenants(query));
  }

  public SearchQueryResult<TenantMemberEntity> searchMembers(final TenantQuery query) {
    return executeSearchRequest(
        () ->
            tenantSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, TENANT_READER_AUTHORIZATION))
                .searchTenantMembers(query));
  }

  @Override
  public TenantServices withAuthentication(final CamundaAuthentication authentication) {
    return new TenantServices(
        brokerClient,
        securityContextProvider,
        tenantSearchClient,
        authentication,
        executorProvider);
  }

  public CompletableFuture<TenantRecord> createTenant(final TenantRequest request) {
    return sendBrokerRequest(
        new BrokerTenantCreateRequest()
            .setTenantId(request.tenantId())
            .setName(request.name())
            .setDescription(request.description()));
  }

  public CompletableFuture<TenantRecord> updateTenant(final TenantRequest request) {
    return sendBrokerRequest(
        new BrokerTenantUpdateRequest(request.tenantId())
            .setName(request.name())
            .setDescription(request.description()));
  }

  public CompletableFuture<TenantRecord> deleteTenant(final String tenantId) {
    return sendBrokerRequest(new BrokerTenantDeleteRequest(tenantId));
  }

  public CompletableFuture<TenantRecord> addMember(final TenantMemberRequest request) {
    return sendBrokerRequest(
        BrokerTenantEntityRequest.createAddRequest()
            .setTenantId(request.tenantId())
            .setEntity(request.entityType(), request.entityId()));
  }

  public CompletableFuture<TenantRecord> removeMember(final TenantMemberRequest request) {
    return sendBrokerRequest(
        BrokerTenantEntityRequest.createRemoveRequest()
            .setTenantId(request.tenantId())
            .setEntity(request.entityType(), request.entityId()));
  }

  public List<TenantEntity> getTenantsByMemberTypeAndMemberIds(
      final Map<EntityType, Set<String>> memberTypesToMemberIds) {
    return search(
            TenantQuery.of(
                q -> q.filter(f -> f.memberIdsByType(memberTypesToMemberIds)).unlimited()))
        .items();
  }

  public TenantEntity getById(final String tenantId) {
    return executeSearchRequest(
        () ->
            tenantSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        Authorization.withAuthorization(TENANT_READER_AUTHORIZATION, tenantId)))
                .getTenant(tenantId));
  }

  public record TenantRequest(Long key, String tenantId, String name, String description) {}

  public record TenantMemberRequest(String tenantId, String entityId, EntityType entityType) {}
}
