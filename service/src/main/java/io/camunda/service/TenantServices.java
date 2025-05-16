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
import io.camunda.search.entities.TenantMemberEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.ErrorMessages;
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
import java.io.Serializable;
import java.util.ArrayList;
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

  public SearchQueryResult<TenantMemberEntity> searchMembers(final TenantQuery query) {
    return tenantSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.tenant().read())))
        .searchTenantMembers(query);
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

  public List<TenantEntity> getTenantsByMemberIds(
      final Set<String> memberIds, final EntityType memberType) {
    return findAll(
        TenantQuery.of(q -> q.filter(b -> b.memberIds(memberIds).childMemberType(memberType))));
  }

  public List<TenantEntity> getTenantsByUserAndGroupsAndRoles(
      final String username, final Set<String> groupIds, final Set<String> roleIds) {
    final var tenants = new ArrayList<>(getTenantsByMemberIds(Set.of(username), EntityType.USER));
    final var groupTenants = getTenantsByMemberIds(groupIds, EntityType.GROUP);
    final var roleTenants = getTenantsByMemberIds(roleIds, EntityType.ROLE);

    tenants.addAll(groupTenants);
    tenants.addAll(roleTenants);
    return tenants.stream().distinct().toList();
  }

  public List<TenantEntity> getTenantsByMappingsAndGroupsAndRoles(
      final Set<String> mappings, final Set<String> groupIds, final Set<String> roleIds) {
    final var tenants = new ArrayList<>(getTenantsByMemberIds(mappings, EntityType.MAPPING));
    final var groupTenants = getTenantsByMemberIds(groupIds, EntityType.GROUP);
    final var roleTenants = getTenantsByMemberIds(roleIds, EntityType.ROLE);

    tenants.addAll(groupTenants);
    tenants.addAll(roleTenants);
    return tenants.stream().distinct().toList();
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
      throw new CamundaSearchException(
          ErrorMessages.ERROR_NOT_FOUND_TENANT.formatted(query),
          CamundaSearchException.Reason.NOT_FOUND);
    } else if (result.total() > 1) {
      throw new CamundaSearchException(
          ErrorMessages.ERROR_NOT_UNIQUE_TENANT.formatted(query),
          CamundaSearchException.Reason.NOT_UNIQUE);
    }

    final var tenantEntity = result.items().stream().findFirst().orElseThrow();
    final var authorization = Authorization.of(a -> a.tenant().read());
    if (!securityContextProvider.isAuthorized(
        tenantEntity.tenantId(), authentication, authorization)) {
      throw new ForbiddenException(authorization);
    }
    return tenantEntity;
  }

  public record TenantDTO(Long key, String tenantId, String name, String description)
      implements Serializable {
    public static TenantDTO fromEntity(final TenantEntity entity) {
      return new TenantDTO(entity.key(), entity.tenantId(), entity.name(), entity.description());
    }
  }

  public record TenantMemberRequest(String tenantId, String entityId, EntityType entityType) {}
}
