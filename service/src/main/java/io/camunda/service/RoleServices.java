/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.ROLE_READ_AUTHORIZATION;

import io.camunda.search.clients.RoleSearchClient;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerRoleEntityRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerRoleUpdateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.role.BrokerRoleCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.role.BrokerRoleDeleteRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.value.DefaultRole;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class RoleServices extends SearchQueryService<RoleServices, RoleQuery, RoleEntity> {

  private final RoleSearchClient roleSearchClient;

  public RoleServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final RoleSearchClient roleSearchClient,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider) {
    super(brokerClient, securityContextProvider, authentication, executorProvider);
    this.roleSearchClient = roleSearchClient;
  }

  @Override
  public SearchQueryResult<RoleEntity> search(final RoleQuery query) {
    return executeSearchRequest(
        () ->
            roleSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, ROLE_READ_AUTHORIZATION))
                .searchRoles(query));
  }

  public SearchQueryResult<RoleMemberEntity> searchMembers(final RoleQuery query) {
    return executeSearchRequest(
        () ->
            roleSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, ROLE_READ_AUTHORIZATION))
                .searchRoleMembers(query));
  }

  public boolean hasMembersOfType(final String roleId, final EntityType entityType) {
    final var query =
        RoleQuery.of(
            builder ->
                builder.filter(
                    filter ->
                        filter
                            .joinParentId(DefaultRole.ADMIN.getId())
                            .memberType(EntityType.USER)));
    final var members = searchMembers(query);
    return members.total() > 0;
  }

  public List<RoleEntity> getRolesByMemberTypeAndMemberIds(
      final Map<EntityType, Set<String>> memberTypesToMemberIds) {
    return search(
            RoleQuery.of(
                roleQuery ->
                    roleQuery
                        .filter(roleFilter -> roleFilter.memberIdsByType(memberTypesToMemberIds))
                        .unlimited()))
        .items();
  }

  @Override
  public RoleServices withAuthentication(final CamundaAuthentication authentication) {
    return new RoleServices(
        brokerClient, securityContextProvider, roleSearchClient, authentication, executorProvider);
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

  public RoleEntity getRole(final String roleId) {
    return executeSearchRequest(
        () ->
            roleSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, withAuthorization(ROLE_READ_AUTHORIZATION, roleId)))
                .getRole(roleId));
  }

  public CompletableFuture<RoleRecord> deleteRole(final String roleId) {
    return sendBrokerRequest(new BrokerRoleDeleteRequest(roleId));
  }

  public CompletableFuture<?> addMember(final RoleMemberRequest request) {
    return sendBrokerRequest(
        BrokerRoleEntityRequest.createAddRequest()
            .setRoleId(request.roleId())
            .setEntity(request.entityType(), request.entityId()));
  }

  public CompletableFuture<?> removeMember(final RoleMemberRequest request) {
    return sendBrokerRequest(
        BrokerRoleEntityRequest.createRemoveRequest()
            .setRoleId(request.roleId())
            .setEntity(request.entityType(), request.entityId()));
  }

  public record CreateRoleRequest(String roleId, String name, String description) {}

  public record UpdateRoleRequest(String roleId, String name, String description) {}

  public record RoleMemberRequest(String roleId, String entityId, EntityType entityType) {}
}
