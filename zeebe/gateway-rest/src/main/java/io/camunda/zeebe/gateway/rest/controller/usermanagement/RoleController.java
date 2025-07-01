/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.MappingQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingServices;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.service.RoleServices.UpdateRoleRequest;
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.MappingSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.MappingSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.RoleClientSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleClientSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.RoleCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.RoleUpdateRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleUserSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleUserSearchResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/roles")
public class RoleController {
  private final RoleServices roleServices;
  private final UserServices userServices;
  private final MappingServices mappingServices;
  private final GroupServices groupServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public RoleController(
      final RoleServices roleServices,
      final UserServices userServices,
      final MappingServices mappingServices,
      final GroupServices groupServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.roleServices = roleServices;
    this.userServices = userServices;
    this.mappingServices = mappingServices;
    this.groupServices = groupServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createRole(
      @RequestBody final RoleCreateRequest createRoleRequest) {
    return RequestMapper.toRoleCreateRequest(createRoleRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createRole);
  }

  private CompletableFuture<ResponseEntity<Object>> createRole(
      final CreateRoleRequest createRoleRequest) {
    return RequestMapper.executeServiceMethod(
        () ->
            roleServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createRole(createRoleRequest),
        ResponseMapper::toRoleCreateResponse);
  }

  @CamundaPutMapping(path = "/{roleId}")
  public CompletableFuture<ResponseEntity<Object>> updateRole(
      @PathVariable final String roleId, @RequestBody final RoleUpdateRequest roleUpdateRequest) {
    return RequestMapper.toRoleUpdateRequest(roleUpdateRequest, roleId)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateRole);
  }

  public CompletableFuture<ResponseEntity<Object>> updateRole(
      final UpdateRoleRequest updateRoleRequest) {
    return RequestMapper.executeServiceMethod(
        () ->
            roleServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .updateRole(updateRoleRequest),
        ResponseMapper::toRoleUpdateResponse);
  }

  @CamundaDeleteMapping(path = "/{roleId}")
  public CompletableFuture<ResponseEntity<Object>> deleteRole(@PathVariable final String roleId) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            roleServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .deleteRole(roleId));
  }

  @CamundaGetMapping(path = "/{roleId}")
  public ResponseEntity<Object> getRole(@PathVariable final String roleId) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toRole(
                  roleServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getRole(roleId)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<RoleSearchQueryResult> searchRoles(
      @RequestBody(required = false) final RoleSearchQueryRequest query) {
    return SearchQueryRequestMapper.toRoleQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<RoleSearchQueryResult> search(final RoleQuery query) {
    try {
      final var result =
          roleServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toRoleSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  @CamundaPostMapping(path = "/{roleId}/users/search")
  public ResponseEntity<RoleUserSearchResult> searchUsersByRole(
      @PathVariable final String roleId,
      @RequestBody(required = false) final RoleUserSearchQueryRequest query) {
    return SearchQueryRequestMapper.toRoleQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            userQuery -> searchUsersInRole(roleId, userQuery));
  }

  private ResponseEntity<RoleUserSearchResult> searchUsersInRole(
      final String roleId, final RoleQuery query) {
    try {
      final var result =
          roleServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .searchMembers(buildRoleMemberQuery(roleId, EntityType.USER, query));
      return ResponseEntity.ok(SearchQueryResponseMapper.toRoleUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaPostMapping(path = "/{roleId}/clients/search")
  public ResponseEntity<RoleClientSearchResult> searchClientsByRole(
      @PathVariable final String roleId,
      @RequestBody(required = false) final RoleClientSearchQueryRequest query) {
    return SearchQueryRequestMapper.toRoleQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            roleQuery -> searchClientsInRole(roleId, roleQuery));
  }

  private ResponseEntity<RoleClientSearchResult> searchClientsInRole(
      final String tenantId, final RoleQuery query) {
    try {
      final var result =
          roleServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .searchMembers(buildRoleMemberQuery(tenantId, EntityType.CLIENT, query));
      return ResponseEntity.ok(SearchQueryResponseMapper.toRoleClientSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private RoleQuery buildRoleMemberQuery(
      final String roleId, final EntityType memberType, final RoleQuery query) {
    return query.toBuilder()
        .filter(query.filter().toBuilder().joinParentId(roleId).memberType(memberType).build())
        .build();
  }

  @CamundaPostMapping(path = "/{roleId}/mapping-rules/search")
  public ResponseEntity<MappingSearchQueryResult> searchMappingRulesByRole(
      @PathVariable final String roleId,
      @RequestBody(required = false) final MappingSearchQueryRequest query) {
    return SearchQueryRequestMapper.toMappingQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mappingQuery -> searchMappingRulesInRole(roleId, mappingQuery));
  }

  private ResponseEntity<MappingSearchQueryResult> searchMappingRulesInRole(
      final String roleId, final MappingQuery mappingQuery) {
    try {
      final var composedMappingQuery = buildMappingQuery(roleId, mappingQuery);
      final var result =
          mappingServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(composedMappingQuery);
      return ResponseEntity.ok(SearchQueryResponseMapper.toMappingSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private MappingQuery buildMappingQuery(final String roleId, final MappingQuery mappingQuery) {
    return mappingQuery.toBuilder()
        .filter(mappingQuery.filter().toBuilder().roleId(roleId).build())
        .build();
  }

  @CamundaPutMapping(
      path = "/{roleId}/users/{username}",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> addUserToRole(
      @PathVariable final String roleId, @PathVariable final String username) {
    return RequestMapper.toRoleMemberRequest(roleId, username, EntityType.USER)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::addMemberToRole);
  }

  @CamundaPutMapping(
      path = "/{roleId}/clients/{clientId}",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> addClientToRole(
      @PathVariable final String roleId, @PathVariable final String clientId) {
    return RequestMapper.toRoleMemberRequest(roleId, clientId, EntityType.CLIENT)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::addMemberToRole);
  }

  @CamundaPutMapping(
      path = "/{roleId}/groups/{groupId}",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> addGroupToRole(
      @PathVariable final String roleId, @PathVariable final String groupId) {
    return RequestMapper.toRoleMemberRequest(roleId, groupId, EntityType.GROUP)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::addMemberToRole);
  }

  private CompletableFuture<ResponseEntity<Object>> addMemberToRole(
      final RoleMemberRequest request) {
    return RequestMapper.executeServiceMethodWithAcceptedResult(
        () ->
            roleServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .addMember(request));
  }

  @CamundaPutMapping(
      path = "/{roleId}/mappings/{mappingId}",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> assignMappingToRole(
      @PathVariable final String roleId, @PathVariable final String mappingId) {
    return RequestMapper.toRoleMemberRequest(roleId, mappingId, EntityType.MAPPING)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::addMemberToRole);
  }

  @CamundaDeleteMapping(path = "/{roleId}/mappings/{mappingId}")
  public CompletableFuture<ResponseEntity<Object>> removeMappingFromRole(
      @PathVariable final String roleId, @PathVariable final String mappingId) {
    return RequestMapper.toRoleMemberRequest(roleId, mappingId, EntityType.MAPPING)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::removeMemberFromRole);
  }

  @CamundaDeleteMapping(path = "/{roleId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> removeUserFromRole(
      @PathVariable final String roleId, @PathVariable final String username) {
    return RequestMapper.toRoleMemberRequest(roleId, username, EntityType.USER)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::removeMemberFromRole);
  }

  @CamundaDeleteMapping(path = "/{roleId}/clients/{clientId}")
  public CompletableFuture<ResponseEntity<Object>> removeClientFromRole(
      @PathVariable final String roleId, @PathVariable final String clientId) {
    return RequestMapper.toRoleMemberRequest(roleId, clientId, EntityType.CLIENT)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::removeMemberFromRole);
  }

  @CamundaDeleteMapping(path = "/{roleId}/groups/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> removeGroupFromRole(
      @PathVariable final String roleId, @PathVariable final String groupId) {
    return RequestMapper.toRoleMemberRequest(roleId, groupId, EntityType.GROUP)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::removeMemberFromRole);
  }

  @CamundaPostMapping(path = "/{roleId}/groups/search")
  public ResponseEntity<GroupSearchQueryResult> searchGroupsByRole(
      @PathVariable final String roleId,
      @RequestBody(required = false) final GroupSearchQueryRequest query) {

    return SearchQueryRequestMapper.toGroupQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            groupQuery -> searchGroupsInRole(roleId, groupQuery));
  }

  private ResponseEntity<GroupSearchQueryResult> searchGroupsInRole(
      final String roleId, final GroupQuery query) {
    try {
      final var composedGroupQuery = buildGroupQuery(roleId, query);
      final var result =
          groupServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(composedGroupQuery);
      return ResponseEntity.ok(SearchQueryResponseMapper.toGroupSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private GroupQuery buildGroupQuery(final String roleId, final GroupQuery groupQuery) {
    return groupQuery.toBuilder()
        .filter(groupQuery.filter().toBuilder().roleId(roleId).build())
        .build();
  }

  private CompletableFuture<ResponseEntity<Object>> removeMemberFromRole(
      final RoleMemberRequest request) {

    return RequestMapper.executeServiceMethodWithAcceptedResult(
        () ->
            roleServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .removeMember(request));
  }
}
