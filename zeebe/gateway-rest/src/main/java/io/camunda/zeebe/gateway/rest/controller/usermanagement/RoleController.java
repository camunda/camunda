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
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.RoleValidator;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.service.RoleServices.UpdateRoleRequest;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/roles")
public class RoleController {
  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final RoleMapper roleMapper;

  public RoleController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider,
      final IdentifierValidator identifierValidator) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
    roleMapper = new RoleMapper(new RoleRequestValidator(new RoleValidator(identifierValidator)));
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createRole(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final RoleCreateRequest createRoleRequest) {
    return roleMapper
        .toRoleCreateRequest(createRoleRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> createRole(physicalTenantId, request));
  }

  private CompletableFuture<ResponseEntity<Object>> createRole(
      final String physicalTenantId, final CreateRoleRequest createRoleRequest) {
    final var roleServices = serviceRegistry.roleServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> roleServices.createRole(createRoleRequest, authentication),
        ResponseMapper::toRoleCreateResponse,
        HttpStatus.CREATED);
  }

  @CamundaPutMapping(path = "/{roleId}")
  public CompletableFuture<ResponseEntity<Object>> updateRole(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String roleId,
      @RequestBody final RoleUpdateRequest roleUpdateRequest) {
    return roleMapper
        .toRoleUpdateRequest(roleUpdateRequest, roleId)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> updateRole(physicalTenantId, request));
  }

  public CompletableFuture<ResponseEntity<Object>> updateRole(
      final String physicalTenantId, final UpdateRoleRequest updateRoleRequest) {
    final var roleServices = serviceRegistry.roleServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> roleServices.updateRole(updateRoleRequest, authentication),
        ResponseMapper::toRoleUpdateResponse,
        HttpStatus.OK);
  }

  @CamundaDeleteMapping(path = "/{roleId}")
  public CompletableFuture<ResponseEntity<Object>> deleteRole(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final String roleId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> serviceRegistry.roleServices(physicalTenantId).deleteRole(roleId, authentication));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{roleId}")
  public ResponseEntity<Object> getRole(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final String roleId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toRole(
                  serviceRegistry.roleServices(physicalTenantId).getRole(roleId, authentication)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<RoleSearchQueryResult> searchRoles(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final RoleSearchQueryRequest query) {
    return SearchQueryRequestMapper.toRoleQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, q -> search(physicalTenantId, q));
  }

  private ResponseEntity<RoleSearchQueryResult> search(
      final String physicalTenantId, final RoleQuery query) {
    final var roleServices = serviceRegistry.roleServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = roleServices.search(query, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toRoleSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{roleId}/users/search")
  public ResponseEntity<RoleUserSearchResult> searchUsersByRole(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String roleId,
      @RequestBody(required = false) final RoleUserSearchQueryRequest query) {
    return SearchQueryRequestMapper.toRoleMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            userQuery -> searchUsersInRole(physicalTenantId, roleId, userQuery));
  }

  private ResponseEntity<RoleUserSearchResult> searchUsersInRole(
      final String physicalTenantId, final String roleId, final RoleMemberQuery query) {
    final var roleServices = serviceRegistry.roleServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result =
          roleServices.searchMembers(
              buildRoleMemberQuery(roleId, EntityType.USER, query), authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toRoleUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{roleId}/clients/search")
  public ResponseEntity<RoleClientSearchResult> searchClientsByRole(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String roleId,
      @RequestBody(required = false) final RoleClientSearchQueryRequest query) {
    return SearchQueryRequestMapper.toRoleMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            roleQuery -> searchClientsInRole(physicalTenantId, roleId, roleQuery));
  }

  private ResponseEntity<RoleClientSearchResult> searchClientsInRole(
      final String physicalTenantId, final String tenantId, final RoleMemberQuery query) {
    final var roleServices = serviceRegistry.roleServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result =
          roleServices.searchMembers(
              buildRoleMemberQuery(tenantId, EntityType.CLIENT, query), authentication);
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
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String roleId,
      @RequestBody(required = false) final MappingRuleSearchQueryRequest query) {
    return SearchQueryRequestMapper.toMappingRuleQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mappingQuery -> searchMappingRulesInRole(physicalTenantId, roleId, mappingQuery));
  }

  private ResponseEntity<MappingRuleSearchQueryResult> searchMappingRulesInRole(
      final String physicalTenantId, final String roleId, final MappingRuleQuery mappingRuleQuery) {
    final var mappingServices = serviceRegistry.mappingRuleServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var composedMappingQuery = buildMappingQuery(roleId, mappingRuleQuery);
      final var result = mappingServices.search(composedMappingQuery, authentication);
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
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String roleId,
      @PathVariable final String username) {
    return roleMapper
        .toRoleMemberRequest(roleId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> addMemberToRole(physicalTenantId, request));
  }

  @CamundaPutMapping(path = "/{roleId}/clients/{clientId}")
  public CompletableFuture<ResponseEntity<Object>> assignRoleToClient(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String roleId,
      @PathVariable final String clientId) {
    return roleMapper
        .toRoleMemberRequest(roleId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> addMemberToRole(physicalTenantId, request));
  }

  @CamundaPutMapping(path = "/{roleId}/groups/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> assignRoleToGroup(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String roleId,
      @PathVariable final String groupId) {
    return roleMapper
        .toRoleMemberRequest(roleId, groupId, EntityType.GROUP)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> addMemberToRole(physicalTenantId, request));
  }

  private CompletableFuture<ResponseEntity<Object>> addMemberToRole(
      final String physicalTenantId, final RoleMemberRequest request) {
    final var roleServices = serviceRegistry.roleServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> roleServices.addMember(request, authentication));
  }

  @CamundaPutMapping(path = "/{roleId}/mapping-rules/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> assignRoleToMappingRule(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String roleId,
      @PathVariable final String mappingRuleId) {
    return roleMapper
        .toRoleMemberRequest(roleId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> addMemberToRole(physicalTenantId, request));
  }

  @CamundaDeleteMapping(path = "/{roleId}/mapping-rules/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> unassignRoleFromMappingRule(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String roleId,
      @PathVariable final String mappingRuleId) {
    return roleMapper
        .toRoleMemberRequest(roleId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> removeMemberFromRole(physicalTenantId, request));
  }

  @CamundaDeleteMapping(path = "/{roleId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> unassignRoleFromUser(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String roleId,
      @PathVariable final String username) {
    return roleMapper
        .toRoleMemberRequest(roleId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> removeMemberFromRole(physicalTenantId, request));
  }

  @CamundaDeleteMapping(path = "/{roleId}/clients/{clientId}")
  public CompletableFuture<ResponseEntity<Object>> unassignRoleFromClient(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String roleId,
      @PathVariable final String clientId) {
    return roleMapper
        .toRoleMemberRequest(roleId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> removeMemberFromRole(physicalTenantId, request));
  }

  @CamundaDeleteMapping(path = "/{roleId}/groups/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> unassignRoleFromGroup(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String roleId,
      @PathVariable final String groupId) {
    return roleMapper
        .toRoleMemberRequest(roleId, groupId, EntityType.GROUP)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> removeMemberFromRole(physicalTenantId, request));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{roleId}/groups/search")
  public ResponseEntity<RoleGroupSearchResult> searchGroupsByRole(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String roleId,
      @RequestBody(required = false) final RoleGroupSearchQueryRequest query) {

    return SearchQueryRequestMapper.toRoleMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            roleQuery -> searchGroupsInRole(physicalTenantId, roleId, roleQuery));
  }

  private ResponseEntity<RoleGroupSearchResult> searchGroupsInRole(
      final String physicalTenantId, final String roleId, final RoleMemberQuery query) {
    final var roleServices = serviceRegistry.roleServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result =
          roleServices.searchMembers(
              buildRoleMemberQuery(roleId, EntityType.GROUP, query), authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toRoleGroupSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> removeMemberFromRole(
      final String physicalTenantId, final RoleMemberRequest request) {
    final var roleServices = serviceRegistry.roleServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> roleServices.removeMember(request, authentication));
  }
}
