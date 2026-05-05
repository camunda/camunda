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
import io.camunda.search.query.RoleMemberQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
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
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.roleSearchClient = roleSearchClient;
  }

  @Override
  public SearchQueryResult<RoleEntity> search(
      final RoleQuery query,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return executeSearchRequest(
        () ->
            roleSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, ROLE_READ_AUTHORIZATION))
                .withPhysicalTenant(physicalTenantId)
                .searchRoles(query));
  }

  public SearchQueryResult<RoleMemberEntity> searchMembers(
      final RoleMemberQuery query,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return executeSearchRequest(
        () ->
            roleSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, ROLE_READ_AUTHORIZATION))
                .withPhysicalTenant(physicalTenantId)
                .searchRoleMembers(query));
  }

  public boolean hasMembersOfType(
      final String roleId,
      final EntityType entityType,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    final var query =
        RoleMemberQuery.of(
            builder ->
                builder.filter(
                    filter ->
                        filter.roleId(DefaultRole.ADMIN.getId()).memberType(EntityType.USER)));
    final var members = searchMembers(query, authentication, physicalTenantId);
    return members.total() > 0;
  }

  public List<RoleEntity> getRolesByMemberTypeAndMemberIds(
      final Map<EntityType, Set<String>> memberTypesToMemberIds,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return search(
            RoleQuery.of(
                roleQuery ->
                    roleQuery
                        .filter(roleFilter -> roleFilter.memberIdsByType(memberTypesToMemberIds))
                        .unlimited()),
            authentication,
            physicalTenantId)
        .items();
  }

  public CompletableFuture<RoleRecord> createRole(
      final CreateRoleRequest request,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return sendBrokerRequest(
        new BrokerRoleCreateRequest()
            .setRoleId(request.roleId())
            .setName(request.name())
            .setDescription(request.description()),
        authentication);
  }

  public CompletableFuture<RoleRecord> updateRole(
      final UpdateRoleRequest updateRoleRequest,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return sendBrokerRequest(
        new BrokerRoleUpdateRequest(updateRoleRequest.roleId())
            .setName(updateRoleRequest.name())
            .setDescription(updateRoleRequest.description()),
        authentication);
  }

  public RoleEntity getRole(
      final String roleId,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return executeSearchRequest(
        () ->
            roleSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, withAuthorization(ROLE_READ_AUTHORIZATION, roleId)))
                .withPhysicalTenant(physicalTenantId)
                .getRole(roleId));
  }

  public CompletableFuture<RoleRecord> deleteRole(
      final String roleId,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return sendBrokerRequest(new BrokerRoleDeleteRequest(roleId), authentication);
  }

  public CompletableFuture<?> addMember(
      final RoleMemberRequest request,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return sendBrokerRequest(
        BrokerRoleEntityRequest.createAddRequest()
            .setRoleId(request.roleId())
            .setEntity(request.entityType(), request.entityId()),
        authentication);
  }

  public CompletableFuture<?> removeMember(
      final RoleMemberRequest request,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return sendBrokerRequest(
        BrokerRoleEntityRequest.createRemoveRequest()
            .setRoleId(request.roleId())
            .setEntity(request.entityType(), request.entityId()),
        authentication);
  }

  public record CreateRoleRequest(String roleId, String name, String description) {}

  public record UpdateRoleRequest(String roleId, String name, String description) {}

  public record RoleMemberRequest(String roleId, String entityId, EntityType entityType) {}
}
