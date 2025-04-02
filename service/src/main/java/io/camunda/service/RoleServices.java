/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.RoleSearchClient;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.ErrorMessages;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerRoleEntityRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerRoleUpdateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.role.BrokerRoleCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.role.BrokerRoleDeleteRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class RoleServices extends SearchQueryService<RoleServices, RoleQuery, RoleEntity> {

  private final RoleSearchClient roleSearchClient;

  public RoleServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final RoleSearchClient roleSearchClient,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.roleSearchClient = roleSearchClient;
  }

  @Override
  public SearchQueryResult<RoleEntity> search(final RoleQuery query) {
    return roleSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.role().read())))
        .searchRoles(query);
  }

  public SearchQueryResult<RoleEntity> getMemberRoles(final long memberKey, final RoleQuery query) {
    return search(
        query.toBuilder().filter(query.filter().toBuilder().memberKey(memberKey).build()).build());
  }

  public List<RoleEntity> getRolesByMemberKeys(final Set<Long> memberKeys) {
    return findAll(RoleQuery.of(q -> q.filter(f -> f.memberKeys(memberKeys))));
  }

  public List<RoleEntity> findAll(final RoleQuery query) {
    return roleSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.role().read())))
        .findAllRoles(query);
  }

  @Override
  public RoleServices withAuthentication(final Authentication authentication) {
    return new RoleServices(
        brokerClient, securityContextProvider, roleSearchClient, authentication);
  }

  public CompletableFuture<RoleRecord> createRole(final CreateRoleRequest request) {
    return sendBrokerRequest(
        new BrokerRoleCreateRequest()
            .setRoleId(request.roleId())
            .setName(request.name())
            .setDescription(request.description()));
  }

  public CompletableFuture<RoleRecord> updateRole(final UpdateRoleRequest updateRoleRequest) {
    return sendBrokerRequest(
        new BrokerRoleUpdateRequest(updateRoleRequest.roleId())
            .setName(updateRoleRequest.name())
            .setDescription(updateRoleRequest.description()));
  }

  public RoleEntity getRole(final Long roleKey) {
    return findRole(roleKey)
        .orElseThrow(
            () ->
                new CamundaSearchException(
                    ErrorMessages.ERROR_NOT_FOUND_ROLE_BY_KEY.formatted(roleKey),
                    CamundaSearchException.Reason.NOT_FOUND));
  }

  public Optional<RoleEntity> findRole(final Long roleKey) {
    return search(SearchQueryBuilders.roleSearchQuery().filter(f -> f.roleKey(roleKey)).build())
        .items()
        .stream()
        .findFirst();
  }

  public Optional<RoleEntity> findRole(final String name) {
    return search(SearchQueryBuilders.roleSearchQuery().filter(f -> f.name(name)).build())
        .items()
        .stream()
        .findFirst();
  }

  public CompletableFuture<RoleRecord> deleteRole(final String roleId) {
    return sendBrokerRequest(new BrokerRoleDeleteRequest(roleId));
  }

  public CompletableFuture<?> addMember(
      final String roleId, final EntityType entityType, final String entityId) {
    return sendBrokerRequest(
        BrokerRoleEntityRequest.createAddRequest()
            .setRoleId(roleId)
            .setEntity(entityType, entityId));
  }

  public CompletableFuture<?> removeMember(
      final Long roleKey, final EntityType entityType, final long entityKey) {
    return sendBrokerRequest(
        BrokerRoleEntityRequest.createRemoveRequest()
            .setRoleId(String.valueOf(roleKey))
            .setEntity(entityType, String.valueOf(entityKey)));
  }

  public record CreateRoleRequest(String roleId, String name, String description) {}

  public record UpdateRoleRequest(String roleId, String name, String description) {}
}
