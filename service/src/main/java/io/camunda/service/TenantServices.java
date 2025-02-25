/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.TenantSearchClient;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerTenantEntityRequest;
import io.camunda.zeebe.gateway.impl.broker.request.tenant.BrokerTenantCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.tenant.BrokerTenantDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.tenant.BrokerTenantUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class TenantServices extends SearchQueryService<TenantServices, TenantQuery, TenantEntity> {

  private final TenantSearchClient tenantSearchClient;

  public TenantServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final TenantSearchClient tenantSearchClient,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.tenantSearchClient = tenantSearchClient;
  }

  @Override
  public SearchQueryResult<TenantEntity> search(final TenantQuery query) {
    return tenantSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.tenant().read())))
        .searchTenants(query);
  }

  public List<TenantEntity> findAll(final TenantQuery query) {
    return tenantSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.tenant().read())))
        .findAllTenants(query);
  }

  @Override
  public TenantServices withAuthentication(final Authentication authentication) {
    return new TenantServices(
        brokerClient, securityContextProvider, tenantSearchClient, authentication);
  }

  public CompletableFuture<TenantRecord> createTenant(final TenantDTO request) {
    return sendBrokerRequest(
        new BrokerTenantCreateRequest()
            .setTenantId(request.tenantId())
            .setName(request.name())
            .setDescription(request.description()));
  }

  public CompletableFuture<TenantRecord> updateTenant(final TenantDTO request) {
    return sendBrokerRequest(
        new BrokerTenantUpdateRequest(request.tenantId())
            .setName(request.name())
            .setDescription(request.description()));
  }

  public CompletableFuture<TenantRecord> deleteTenant(final String tenantId) {
    return sendBrokerRequest(new BrokerTenantDeleteRequest(tenantId));
  }

  public CompletableFuture<TenantRecord> addMember(
      final Long tenantKey, final EntityType entityType, final long entityKey) {
    return sendBrokerRequest(
        BrokerTenantEntityRequest.createAddRequest()
            .setTenantKey(tenantKey)
            .setEntity(entityType, entityKey));
  }

  public CompletableFuture<TenantRecord> addMember(
      final String tenantId, final EntityType entityType, final String entityId) {
    return sendBrokerRequest(
        BrokerTenantEntityRequest.createAddRequest()
            .setTenantId(tenantId)
            .setEntity(entityType, entityId));
  }

  public CompletableFuture<TenantRecord> removeMember(
      final Long tenantKey, final EntityType entityType, final long entityKey) {
    return sendBrokerRequest(
        BrokerTenantEntityRequest.createRemoveRequest()
            .setTenantKey(tenantKey)
            .setEntity(entityType, entityKey));
  }

  public CompletableFuture<TenantRecord> removeMember(
      final String tenantId, final EntityType entityType, final String entityId) {
    return sendBrokerRequest(
        BrokerTenantEntityRequest.createRemoveRequest()
            .setTenantId(tenantId)
            .setEntity(entityType, entityId));
  }

  public Collection<TenantEntity> getTenantsByMemberKey(final long memberKey) {
    return getTenantsByMemberKeys(Set.of(memberKey));
  }

  public List<TenantEntity> getTenantsByMemberKeys(final Set<Long> memberKeys) {
    return findAll(TenantQuery.of(q -> q.filter(b -> b.memberKeys(memberKeys))));
  }

  public TenantEntity getById(final String tenantId) {
    return getSingle(TenantQuery.of(q -> q.filter(f -> f.tenantId(tenantId))));
  }

  private TenantEntity getSingle(final TenantQuery query) {
    final var result =
        tenantSearchClient
            .withSecurityContext(securityContextProvider.provideSecurityContext(authentication))
            .searchTenants(query);
    if (result.total() < 1) {
      throw new NotFoundException("Tenant matching %s not found".formatted(query));
    } else if (result.total() > 1) {
      throw new CamundaSearchException("Found multiple tenants matching %s".formatted(query));
    }

    final var tenantEntity = result.items().stream().findFirst().orElseThrow();
    final var authorization = Authorization.of(a -> a.tenant().read());
    if (!securityContextProvider.isAuthorized(
        tenantEntity.tenantId(), authentication, authorization)) {
      throw new ForbiddenException(authorization);
    }
    return tenantEntity;
  }

  public record TenantDTO(Long key, String tenantId, String name, String description) {
    public static TenantDTO fromEntity(final TenantEntity entity) {
      return new TenantDTO(entity.key(), entity.tenantId(), entity.name(), entity.description());
    }
  }
}
