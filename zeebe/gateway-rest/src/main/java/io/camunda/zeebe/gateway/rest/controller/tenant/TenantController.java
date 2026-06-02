/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.tenant;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.mapper.TenantMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.validator.TenantRequestValidator;
import io.camunda.gateway.protocol.model.GroupSearchQueryResult;
import io.camunda.gateway.protocol.model.MappingRuleSearchQueryRequest;
import io.camunda.gateway.protocol.model.MappingRuleSearchQueryResult;
import io.camunda.gateway.protocol.model.RoleSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleSearchQueryResult;
import io.camunda.gateway.protocol.model.TenantClientSearchQueryRequest;
import io.camunda.gateway.protocol.model.TenantClientSearchResult;
import io.camunda.gateway.protocol.model.TenantCreateRequest;
import io.camunda.gateway.protocol.model.TenantGroupSearchQueryRequest;
import io.camunda.gateway.protocol.model.TenantGroupSearchResult;
import io.camunda.gateway.protocol.model.TenantResult;
import io.camunda.gateway.protocol.model.TenantSearchQueryRequest;
import io.camunda.gateway.protocol.model.TenantSearchQueryResult;
import io.camunda.gateway.protocol.model.TenantUpdateRequest;
import io.camunda.gateway.protocol.model.TenantUserSearchQueryRequest;
import io.camunda.gateway.protocol.model.TenantUserSearchResult;
import io.camunda.gateway.protocol.model.UserSearchResult;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.TenantMemberQuery;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.TenantValidator;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.service.TenantServices.TenantRequest;
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
import org.springframework.web.bind.annotation.*;

@CamundaRestController
@RequestMapping("/v2/tenants")
public class TenantController {
  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final TenantMapper tenantMapper;

  public TenantController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider,
      final IdentifierValidator identifierValidator) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
    tenantMapper =
        new TenantMapper(new TenantRequestValidator(new TenantValidator(identifierValidator)));
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createTenant(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final TenantCreateRequest createTenantRequest) {
    return tenantMapper
        .toTenantCreateDto(createTenantRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> createTenant(physicalTenantId, request));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{tenantId}")
  public ResponseEntity<TenantResult> getTenant(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final String tenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toTenant(
                  serviceRegistry
                      .tenantServices(physicalTenantId)
                      .getById(tenantId, authentication)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<TenantSearchQueryResult> searchTenants(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final TenantSearchQueryRequest query) {
    return SearchQueryRequestMapper.toTenantQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, q -> search(physicalTenantId, q));
  }

  @CamundaPutMapping(path = "/{tenantId}")
  public CompletableFuture<ResponseEntity<Object>> updateTenant(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String tenantId,
      @RequestBody final TenantUpdateRequest tenantUpdateRequest) {
    return tenantMapper
        .toTenantUpdateDto(tenantId, tenantUpdateRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> updateTenant(physicalTenantId, request));
  }

  @CamundaPutMapping(path = "/{tenantId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> assignUserToTenant(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String tenantId,
      @PathVariable final String username) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> addMemberToTenant(physicalTenantId, request));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{tenantId}/users/search")
  public ResponseEntity<TenantUserSearchResult> searchUsersInTenant(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String tenantId,
      @RequestBody(required = false) final TenantUserSearchQueryRequest query) {
    return SearchQueryRequestMapper.toTenantMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            tenantQuery -> searchUsersInTenant(physicalTenantId, tenantId, tenantQuery));
  }

  @CamundaPutMapping(path = "/{tenantId}/clients/{clientId}")
  public CompletableFuture<ResponseEntity<Object>> assignClientToTenant(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String tenantId,
      @PathVariable final String clientId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> addMemberToTenant(physicalTenantId, request));
  }

  @CamundaPutMapping(path = "/{tenantId}/mapping-rules/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> assignMappingRuleToTenant(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String tenantId,
      @PathVariable final String mappingRuleId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> addMemberToTenant(physicalTenantId, request));
  }

  @CamundaPutMapping(path = "/{tenantId}/groups/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> assignGroupToTenant(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String tenantId,
      @PathVariable final String groupId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, groupId, EntityType.GROUP)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> addMemberToTenant(physicalTenantId, request));
  }

  @CamundaPutMapping(path = "/{tenantId}/roles/{roleId}")
  public CompletableFuture<ResponseEntity<Object>> assignRoleToTenant(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String tenantId,
      @PathVariable final String roleId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, roleId, EntityType.ROLE)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> addMemberToTenant(physicalTenantId, request));
  }

  @CamundaDeleteMapping(path = "/{tenantId}")
  public CompletableFuture<ResponseEntity<Object>> deleteTenant(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final String tenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            serviceRegistry
                .tenantServices(physicalTenantId)
                .deleteTenant(tenantId, authentication));
  }

  @CamundaDeleteMapping(path = "/{tenantId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> unassignUserFromTenant(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String tenantId,
      @PathVariable final String username) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> removeMemberFromTenant(physicalTenantId, request));
  }

  @CamundaDeleteMapping(path = "/{tenantId}/clients/{clientId}")
  public CompletableFuture<ResponseEntity<Object>> unassignClientFromTenant(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String tenantId,
      @PathVariable final String clientId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> removeMemberFromTenant(physicalTenantId, request));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{tenantId}/mapping-rules/search")
  public ResponseEntity<MappingRuleSearchQueryResult> searchMappingRulesForTenant(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String tenantId,
      @RequestBody(required = false) final MappingRuleSearchQueryRequest query) {
    return SearchQueryRequestMapper.toMappingRuleQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mappingRuleQuery ->
                searchMappingRulesForTenant(physicalTenantId, tenantId, mappingRuleQuery));
  }

  @CamundaDeleteMapping(path = "/{tenantId}/mapping-rules/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> unassignMappingRuleFromTenant(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String tenantId,
      @PathVariable final String mappingRuleId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> removeMemberFromTenant(physicalTenantId, request));
  }

  @CamundaDeleteMapping(path = "/{tenantId}/groups/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> unassignGroupFromTenant(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String tenantId,
      @PathVariable final String groupId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, groupId, EntityType.GROUP)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> removeMemberFromTenant(physicalTenantId, request));
  }

  @CamundaDeleteMapping(path = "/{tenantId}/roles/{roleId}")
  public CompletableFuture<ResponseEntity<Object>> unassignRoleFromTenant(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String tenantId,
      @PathVariable final String roleId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, roleId, EntityType.ROLE)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> removeMemberFromTenant(physicalTenantId, request));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{tenantId}/groups/search")
  public ResponseEntity<TenantGroupSearchResult> searchGroupIdsInTenant(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String tenantId,
      @RequestBody(required = false) final TenantGroupSearchQueryRequest query) {
    return SearchQueryRequestMapper.toTenantMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            tenantQuery -> searchGroupIdsInTenant(physicalTenantId, tenantId, tenantQuery));
  }

  private ResponseEntity<TenantGroupSearchResult> searchGroupIdsInTenant(
      final String physicalTenantId, final String tenantId, final TenantMemberQuery query) {
    final var tenantServices = serviceRegistry.tenantServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result =
          tenantServices.searchMembers(
              buildTenantMemberQuery(tenantId, EntityType.GROUP, query), authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toTenantGroupSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{tenantId}/roles/search")
  public ResponseEntity<RoleSearchQueryResult> searchRolesInTenant(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String tenantId,
      @RequestBody(required = false) final RoleSearchQueryRequest query) {
    return SearchQueryRequestMapper.toRoleQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            roleQuery -> searchRolesInTenant(physicalTenantId, tenantId, roleQuery));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{tenantId}/clients/search")
  public ResponseEntity<TenantClientSearchResult> searchClientsInTenant(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String tenantId,
      @RequestBody(required = false) final TenantClientSearchQueryRequest query) {
    return SearchQueryRequestMapper.toTenantMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            tenantQuery -> searchClientsInTenant(physicalTenantId, tenantId, tenantQuery));
  }

  private CompletableFuture<ResponseEntity<Object>> createTenant(
      final String physicalTenantId, final TenantRequest tenantRequest) {
    final var tenantServices = serviceRegistry.tenantServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> tenantServices.createTenant(tenantRequest, authentication),
        ResponseMapper::toTenantCreateResponse,
        HttpStatus.CREATED);
  }

  private CompletableFuture<ResponseEntity<Object>> addMemberToTenant(
      final String physicalTenantId, final TenantMemberRequest request) {
    final var tenantServices = serviceRegistry.tenantServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> tenantServices.addMember(request, authentication));
  }

  private CompletableFuture<ResponseEntity<Object>> removeMemberFromTenant(
      final String physicalTenantId, final TenantMemberRequest request) {
    final var tenantServices = serviceRegistry.tenantServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> tenantServices.removeMember(request, authentication));
  }

  private ResponseEntity<TenantSearchQueryResult> search(
      final String physicalTenantId, final TenantQuery query) {
    final var tenantServices = serviceRegistry.tenantServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = tenantServices.search(query, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toTenantSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<TenantUserSearchResult> searchUsersInTenant(
      final String physicalTenantId,
      final String tenantId,
      final TenantMemberQuery tenantMemberQuery) {
    final var tenantServices = serviceRegistry.tenantServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result =
          tenantServices.searchMembers(
              buildTenantMemberQuery(tenantId, EntityType.USER, tenantMemberQuery), authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toTenantUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<TenantClientSearchResult> searchClientsInTenant(
      final String physicalTenantId, final String tenantId, final TenantMemberQuery query) {
    final var tenantServices = serviceRegistry.tenantServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result =
          tenantServices.searchMembers(
              buildTenantMemberQuery(tenantId, EntityType.CLIENT, query), authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toTenantClientSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<MappingRuleSearchQueryResult> searchMappingRulesForTenant(
      final String physicalTenantId,
      final String tenantId,
      final MappingRuleQuery mappingRuleQuery) {
    final var mappingRuleServices = serviceRegistry.mappingRuleServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var composedMappingRuleQuery = buildMappingRuleQuery(tenantId, mappingRuleQuery);
      final var result = mappingRuleServices.search(composedMappingRuleQuery, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toMappingRuleSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<UserSearchResult> searchUsersInTenant(
      final String physicalTenantId, final String tenantId, final UserQuery userQuery) {
    final var userServices = serviceRegistry.userServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var composedUserQuery = buildUserQuery(tenantId, userQuery);
      final var result = userServices.search(composedUserQuery, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<GroupSearchQueryResult> searchGroupsInTenant(
      final String physicalTenantId, final String tenantId, final GroupQuery groupQuery) {
    final var groupServices = serviceRegistry.groupServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var composedGroupQuery = buildGroupQuery(tenantId, groupQuery);
      final var result = groupServices.search(composedGroupQuery, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toGroupSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<RoleSearchQueryResult> searchRolesInTenant(
      final String physicalTenantId, final String tenantId, final RoleQuery roleQuery) {
    final var roleServices = serviceRegistry.roleServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var composedRoleQuery = buildRoleQuery(tenantId, roleQuery);
      final var result = roleServices.search(composedRoleQuery, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toRoleSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private TenantMemberQuery buildTenantMemberQuery(
      final String tenantId, final EntityType memberType, final TenantMemberQuery query) {
    return query.toBuilder()
        .filter(query.filter().toBuilder().tenantId(tenantId).memberType(memberType).build())
        .build();
  }

  private MappingRuleQuery buildMappingRuleQuery(
      final String tenantId, final MappingRuleQuery mappingRuleQuery) {
    return mappingRuleQuery.toBuilder()
        .filter(mappingRuleQuery.filter().toBuilder().tenantId(tenantId).build())
        .build();
  }

  private UserQuery buildUserQuery(final String tenantId, final UserQuery userQuery) {
    return userQuery.toBuilder()
        .filter(userQuery.filter().toBuilder().tenantId(tenantId).build())
        .build();
  }

  private GroupQuery buildGroupQuery(final String tenantId, final GroupQuery groupQuery) {
    return groupQuery.toBuilder()
        .filter(groupQuery.filter().toBuilder().tenantId(tenantId).build())
        .build();
  }

  private RoleQuery buildRoleQuery(final String tenantId, final RoleQuery roleQuery) {
    return roleQuery.toBuilder()
        .filter(roleQuery.filter().toBuilder().tenantId(tenantId).build())
        .build();
  }

  private CompletableFuture<ResponseEntity<Object>> updateTenant(
      final String physicalTenantId, final TenantRequest tenantRequest) {
    final var tenantServices = serviceRegistry.tenantServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> tenantServices.updateTenant(tenantRequest, authentication),
        ResponseMapper::toTenantUpdateResponse,
        HttpStatus.OK);
  }
}
