/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.mapper.RoleMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.validator.RoleRequestValidator;
import io.camunda.gateway.protocol.model.MappingRuleSearchQueryRequest;
import io.camunda.gateway.protocol.model.MappingRuleSearchQueryResult;
import io.camunda.gateway.protocol.model.RoleClientSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleClientSearchResult;
import io.camunda.gateway.protocol.model.RoleCreateRequest;
import io.camunda.gateway.protocol.model.RoleGroupSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleGroupSearchResult;
import io.camunda.gateway.protocol.model.RoleSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleSearchQueryResult;
import io.camunda.gateway.protocol.model.RoleUpdateRequest;
import io.camunda.gateway.protocol.model.RoleUserSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleUserSearchResult;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.RoleMemberQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.RoleValidator;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.service.RoleServices.UpdateRoleRequest;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenant;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/roles")
public class RoleController {
  private final RoleServices roleServices;
  private final MappingRuleServices mappingServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final RoleMapper roleMapper;

  public RoleController(
      final RoleServices roleServices,
      final MappingRuleServices mappingServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final IdentifierValidator identifierValidator) {
    this.roleServices = roleServices;
    this.mappingServices = mappingServices;
    this.authenticationProvider = authenticationProvider;
    roleMapper = new RoleMapper(new RoleRequestValidator(new RoleValidator(identifierValidator)));
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createRole(
      @RequestBody final RoleCreateRequest createRoleRequest,
      @PhysicalTenant final String physicalTenantId) {
    return roleMapper
        .toRoleCreateRequest(createRoleRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> createRole(req, physicalTenantId));
  }

  private CompletableFuture<ResponseEntity<Object>> createRole(
      final CreateRoleRequest createRoleRequest, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> roleServices.createRole(createRoleRequest, authentication, physicalTenantId),
        ResponseMapper::toRoleCreateResponse,
        HttpStatus.CREATED);
  }

  @CamundaPutMapping(path = "/{roleId}")
  public CompletableFuture<ResponseEntity<Object>> updateRole(
      @PathVariable final String roleId,
      @RequestBody final RoleUpdateRequest roleUpdateRequest,
      @PhysicalTenant final String physicalTenantId) {
    return roleMapper
        .toRoleUpdateRequest(roleUpdateRequest, roleId)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> updateRole(req, physicalTenantId));
  }

  public CompletableFuture<ResponseEntity<Object>> updateRole(
      final UpdateRoleRequest updateRoleRequest, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> roleServices.updateRole(updateRoleRequest, authentication, physicalTenantId),
        ResponseMapper::toRoleUpdateResponse,
        HttpStatus.OK);
  }

  @CamundaDeleteMapping(path = "/{roleId}")
  public CompletableFuture<ResponseEntity<Object>> deleteRole(
      @PathVariable final String roleId, @PhysicalTenant final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> roleServices.deleteRole(roleId, authentication, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{roleId}")
  public ResponseEntity<Object> getRole(
      @PathVariable final String roleId, @PhysicalTenant final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toRole(
                  roleServices.getRole(roleId, authentication, physicalTenantId)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<RoleSearchQueryResult> searchRoles(
      @RequestBody(required = false) final RoleSearchQueryRequest query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toRoleQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, q -> search(q, physicalTenantId));
  }

  private ResponseEntity<RoleSearchQueryResult> search(
      final RoleQuery query, final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = roleServices.search(query, authentication, physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toRoleSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{roleId}/users/search")
  public ResponseEntity<RoleUserSearchResult> searchUsersByRole(
      @PathVariable final String roleId,
      @RequestBody(required = false) final RoleUserSearchQueryRequest query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toRoleMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            userQuery -> searchUsersInRole(roleId, userQuery, physicalTenantId));
  }

  private ResponseEntity<RoleUserSearchResult> searchUsersInRole(
      final String roleId, final RoleMemberQuery query, final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result =
          roleServices.searchMembers(
              buildRoleMemberQuery(roleId, EntityType.USER, query),
              authentication,
              physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toRoleUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{roleId}/clients/search")
  public ResponseEntity<RoleClientSearchResult> searchClientsByRole(
      @PathVariable final String roleId,
      @RequestBody(required = false) final RoleClientSearchQueryRequest query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toRoleMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            roleQuery -> searchClientsInRole(roleId, roleQuery, physicalTenantId));
  }

  private ResponseEntity<RoleClientSearchResult> searchClientsInRole(
      final String tenantId, final RoleMemberQuery query, final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result =
          roleServices.searchMembers(
              buildRoleMemberQuery(tenantId, EntityType.CLIENT, query),
              authentication,
              physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toRoleClientSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private RoleMemberQuery buildRoleMemberQuery(
      final String roleId, final EntityType memberType, final RoleMemberQuery query) {
    return query.toBuilder()
        .filter(query.filter().toBuilder().roleId(roleId).memberType(memberType).build())
        .build();
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{roleId}/mapping-rules/search")
  public ResponseEntity<MappingRuleSearchQueryResult> searchMappingRulesByRole(
      @PathVariable final String roleId,
      @RequestBody(required = false) final MappingRuleSearchQueryRequest query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toMappingRuleQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mappingQuery -> searchMappingRulesInRole(roleId, mappingQuery, physicalTenantId));
  }

  private ResponseEntity<MappingRuleSearchQueryResult> searchMappingRulesInRole(
      final String roleId, final MappingRuleQuery mappingRuleQuery, final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var composedMappingQuery = buildMappingQuery(roleId, mappingRuleQuery);
      final var result =
          mappingServices.search(composedMappingQuery, authentication, physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toMappingRuleSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private MappingRuleQuery buildMappingQuery(
      final String roleId, final MappingRuleQuery mappingRuleQuery) {
    return mappingRuleQuery.toBuilder()
        .filter(mappingRuleQuery.filter().toBuilder().roleId(roleId).build())
        .build();
  }

  @CamundaPutMapping(path = "/{roleId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> assignRoleToUser(
      @PathVariable final String roleId,
      @PathVariable final String username,
      @PhysicalTenant final String physicalTenantId) {
    return roleMapper
        .toRoleMemberRequest(roleId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> addMemberToRole(req, physicalTenantId));
  }

  @CamundaPutMapping(path = "/{roleId}/clients/{clientId}")
  public CompletableFuture<ResponseEntity<Object>> assignRoleToClient(
      @PathVariable final String roleId,
      @PathVariable final String clientId,
      @PhysicalTenant final String physicalTenantId) {
    return roleMapper
        .toRoleMemberRequest(roleId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> addMemberToRole(req, physicalTenantId));
  }

  @CamundaPutMapping(path = "/{roleId}/groups/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> assignRoleToGroup(
      @PathVariable final String roleId,
      @PathVariable final String groupId,
      @PhysicalTenant final String physicalTenantId) {
    return roleMapper
        .toRoleMemberRequest(roleId, groupId, EntityType.GROUP)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> addMemberToRole(req, physicalTenantId));
  }

  private CompletableFuture<ResponseEntity<Object>> addMemberToRole(
      final RoleMemberRequest request, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> roleServices.addMember(request, authentication, physicalTenantId));
  }

  @CamundaPutMapping(path = "/{roleId}/mapping-rules/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> assignRoleToMappingRule(
      @PathVariable final String roleId,
      @PathVariable final String mappingRuleId,
      @PhysicalTenant final String physicalTenantId) {
    return roleMapper
        .toRoleMemberRequest(roleId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> addMemberToRole(req, physicalTenantId));
  }

  @CamundaDeleteMapping(path = "/{roleId}/mapping-rules/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> unassignRoleFromMappingRule(
      @PathVariable final String roleId,
      @PathVariable final String mappingRuleId,
      @PhysicalTenant final String physicalTenantId) {
    return roleMapper
        .toRoleMemberRequest(roleId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> removeMemberFromRole(req, physicalTenantId));
  }

  @CamundaDeleteMapping(path = "/{roleId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> unassignRoleFromUser(
      @PathVariable final String roleId,
      @PathVariable final String username,
      @PhysicalTenant final String physicalTenantId) {
    return roleMapper
        .toRoleMemberRequest(roleId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> removeMemberFromRole(req, physicalTenantId));
  }

  @CamundaDeleteMapping(path = "/{roleId}/clients/{clientId}")
  public CompletableFuture<ResponseEntity<Object>> unassignRoleFromClient(
      @PathVariable final String roleId,
      @PathVariable final String clientId,
      @PhysicalTenant final String physicalTenantId) {
    return roleMapper
        .toRoleMemberRequest(roleId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> removeMemberFromRole(req, physicalTenantId));
  }

  @CamundaDeleteMapping(path = "/{roleId}/groups/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> unassignRoleFromGroup(
      @PathVariable final String roleId,
      @PathVariable final String groupId,
      @PhysicalTenant final String physicalTenantId) {
    return roleMapper
        .toRoleMemberRequest(roleId, groupId, EntityType.GROUP)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> removeMemberFromRole(req, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{roleId}/groups/search")
  public ResponseEntity<RoleGroupSearchResult> searchGroupsByRole(
      @PathVariable final String roleId,
      @RequestBody(required = false) final RoleGroupSearchQueryRequest query,
      @PhysicalTenant final String physicalTenantId) {

    return SearchQueryRequestMapper.toRoleMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            roleQuery -> searchGroupsInRole(roleId, roleQuery, physicalTenantId));
  }

  private ResponseEntity<RoleGroupSearchResult> searchGroupsInRole(
      final String roleId, final RoleMemberQuery query, final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result =
          roleServices.searchMembers(
              buildRoleMemberQuery(roleId, EntityType.GROUP, query),
              authentication,
              physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toRoleGroupSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> removeMemberFromRole(
      final RoleMemberRequest request, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> roleServices.removeMember(request, authentication, physicalTenantId));
  }
}
