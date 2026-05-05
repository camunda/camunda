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
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.TenantMemberQuery;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.TenantValidator;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.service.TenantServices.TenantRequest;
import io.camunda.service.UserServices;
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
@RequestMapping("/v2/tenants")
public class TenantController {
  private final TenantServices tenantServices;
  private final UserServices userServices;
  private final MappingRuleServices mappingRuleServices;
  private final GroupServices groupServices;
  private final RoleServices roleServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final TenantMapper tenantMapper;

  public TenantController(
      final TenantServices tenantServices,
      final UserServices userServices,
      final MappingRuleServices mappingRuleServices,
      final GroupServices groupServices,
      final RoleServices roleServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final IdentifierValidator identifierValidator) {
    this.tenantServices = tenantServices;
    this.userServices = userServices;
    this.mappingRuleServices = mappingRuleServices;
    this.groupServices = groupServices;
    this.roleServices = roleServices;
    this.authenticationProvider = authenticationProvider;
    tenantMapper =
        new TenantMapper(new TenantRequestValidator(new TenantValidator(identifierValidator)));
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createTenant(
      @RequestBody final TenantCreateRequest createTenantRequest,
      @PhysicalTenant final String physicalTenantId) {
    return tenantMapper
        .toTenantCreateDto(createTenantRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> createTenant(req, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{tenantId}")
  public ResponseEntity<TenantResult> getTenant(
      @PathVariable final String tenantId, @PhysicalTenant final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toTenant(
                  tenantServices.getById(tenantId, authentication, physicalTenantId)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<TenantSearchQueryResult> searchTenants(
      @RequestBody(required = false) final TenantSearchQueryRequest query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toTenantQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, q -> search(q, physicalTenantId));
  }

  @CamundaPutMapping(path = "/{tenantId}")
  public CompletableFuture<ResponseEntity<Object>> updateTenant(
      @PathVariable final String tenantId,
      @RequestBody final TenantUpdateRequest tenantUpdateRequest,
      @PhysicalTenant final String physicalTenantId) {
    return tenantMapper
        .toTenantUpdateDto(tenantId, tenantUpdateRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> updateTenant(req, physicalTenantId));
  }

  @CamundaPutMapping(path = "/{tenantId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> assignUserToTenant(
      @PathVariable final String tenantId,
      @PathVariable final String username,
      @PhysicalTenant final String physicalTenantId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> addMemberToTenant(req, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{tenantId}/users/search")
  public ResponseEntity<TenantUserSearchResult> searchUsersInTenant(
      @PathVariable final String tenantId,
      @RequestBody(required = false) final TenantUserSearchQueryRequest query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toTenantMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            tenantQuery -> searchUsersInTenant(tenantId, tenantQuery, physicalTenantId));
  }

  @CamundaPutMapping(path = "/{tenantId}/clients/{clientId}")
  public CompletableFuture<ResponseEntity<Object>> assignClientToTenant(
      @PathVariable final String tenantId,
      @PathVariable final String clientId,
      @PhysicalTenant final String physicalTenantId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> addMemberToTenant(req, physicalTenantId));
  }

  @CamundaPutMapping(path = "/{tenantId}/mapping-rules/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> assignMappingRuleToTenant(
      @PathVariable final String tenantId,
      @PathVariable final String mappingRuleId,
      @PhysicalTenant final String physicalTenantId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> addMemberToTenant(req, physicalTenantId));
  }

  @CamundaPutMapping(path = "/{tenantId}/groups/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> assignGroupToTenant(
      @PathVariable final String tenantId,
      @PathVariable final String groupId,
      @PhysicalTenant final String physicalTenantId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, groupId, EntityType.GROUP)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> addMemberToTenant(req, physicalTenantId));
  }

  @CamundaPutMapping(path = "/{tenantId}/roles/{roleId}")
  public CompletableFuture<ResponseEntity<Object>> assignRoleToTenant(
      @PathVariable final String tenantId,
      @PathVariable final String roleId,
      @PhysicalTenant final String physicalTenantId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, roleId, EntityType.ROLE)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> addMemberToTenant(req, physicalTenantId));
  }

  @CamundaDeleteMapping(path = "/{tenantId}")
  public CompletableFuture<ResponseEntity<Object>> deleteTenant(
      @PathVariable final String tenantId, @PhysicalTenant final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> tenantServices.deleteTenant(tenantId, authentication, physicalTenantId));
  }

  @CamundaDeleteMapping(path = "/{tenantId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> unassignUserFromTenant(
      @PathVariable final String tenantId,
      @PathVariable final String username,
      @PhysicalTenant final String physicalTenantId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> removeMemberFromTenant(req, physicalTenantId));
  }

  @CamundaDeleteMapping(path = "/{tenantId}/clients/{clientId}")
  public CompletableFuture<ResponseEntity<Object>> unassignClientFromTenant(
      @PathVariable final String tenantId,
      @PathVariable final String clientId,
      @PhysicalTenant final String physicalTenantId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> removeMemberFromTenant(req, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{tenantId}/mapping-rules/search")
  public ResponseEntity<MappingRuleSearchQueryResult> searchMappingRulesForTenant(
      @PathVariable final String tenantId,
      @RequestBody(required = false) final MappingRuleSearchQueryRequest query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toMappingRuleQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mappingRuleQuery ->
                searchMappingRulesForTenant(tenantId, mappingRuleQuery, physicalTenantId));
  }

  @CamundaDeleteMapping(path = "/{tenantId}/mapping-rules/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> unassignMappingRuleFromTenant(
      @PathVariable final String tenantId,
      @PathVariable final String mappingRuleId,
      @PhysicalTenant final String physicalTenantId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> removeMemberFromTenant(req, physicalTenantId));
  }

  @CamundaDeleteMapping(path = "/{tenantId}/groups/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> unassignGroupFromTenant(
      @PathVariable final String tenantId,
      @PathVariable final String groupId,
      @PhysicalTenant final String physicalTenantId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, groupId, EntityType.GROUP)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> removeMemberFromTenant(req, physicalTenantId));
  }

  @CamundaDeleteMapping(path = "/{tenantId}/roles/{roleId}")
  public CompletableFuture<ResponseEntity<Object>> unassignRoleFromTenant(
      @PathVariable final String tenantId,
      @PathVariable final String roleId,
      @PhysicalTenant final String physicalTenantId) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, roleId, EntityType.ROLE)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> removeMemberFromTenant(req, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{tenantId}/groups/search")
  public ResponseEntity<TenantGroupSearchResult> searchGroupIdsInTenant(
      @PathVariable final String tenantId,
      @RequestBody(required = false) final TenantGroupSearchQueryRequest query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toTenantMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            tenantQuery -> searchGroupIdsInTenant(tenantId, tenantQuery, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{tenantId}/roles/search")
  public ResponseEntity<RoleSearchQueryResult> searchRolesInTenant(
      @PathVariable final String tenantId,
      @RequestBody(required = false) final RoleSearchQueryRequest query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toRoleQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            roleQuery -> searchRolesInTenant(tenantId, roleQuery, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{tenantId}/clients/search")
  public ResponseEntity<TenantClientSearchResult> searchClientsInTenant(
      @PathVariable final String tenantId,
      @RequestBody(required = false) final TenantClientSearchQueryRequest query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toTenantMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            tenantQuery -> searchClientsInTenant(tenantId, tenantQuery, physicalTenantId));
  }

  private CompletableFuture<ResponseEntity<Object>> createTenant(
      final TenantRequest tenantRequest, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> tenantServices.createTenant(tenantRequest, authentication, physicalTenantId),
        ResponseMapper::toTenantCreateResponse,
        HttpStatus.CREATED);
  }

  private CompletableFuture<ResponseEntity<Object>> addMemberToTenant(
      final TenantMemberRequest request, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> tenantServices.addMember(request, authentication, physicalTenantId));
  }

  private CompletableFuture<ResponseEntity<Object>> removeMemberFromTenant(
      final TenantMemberRequest request, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> tenantServices.removeMember(request, authentication, physicalTenantId));
  }

  private ResponseEntity<TenantSearchQueryResult> search(
      final TenantQuery query, final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = tenantServices.search(query, authentication, physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toTenantSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<TenantUserSearchResult> searchUsersInTenant(
      final String tenantId,
      final TenantMemberQuery tenantMemberQuery,
      final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result =
          tenantServices.searchMembers(
              buildTenantMemberQuery(tenantId, EntityType.USER, tenantMemberQuery),
              authentication,
              physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toTenantUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<TenantClientSearchResult> searchClientsInTenant(
      final String tenantId, final TenantMemberQuery query, final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result =
          tenantServices.searchMembers(
              buildTenantMemberQuery(tenantId, EntityType.CLIENT, query),
              authentication,
              physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toTenantClientSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<TenantGroupSearchResult> searchGroupIdsInTenant(
      final String tenantId, final TenantMemberQuery query, final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result =
          tenantServices.searchMembers(
              buildTenantMemberQuery(tenantId, EntityType.GROUP, query),
              authentication,
              physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toTenantGroupSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<MappingRuleSearchQueryResult> searchMappingRulesForTenant(
      final String tenantId,
      final MappingRuleQuery mappingRuleQuery,
      final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var composedMappingRuleQuery = buildMappingRuleQuery(tenantId, mappingRuleQuery);
      final var result =
          mappingRuleServices.search(composedMappingRuleQuery, authentication, physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toMappingRuleSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<RoleSearchQueryResult> searchRolesInTenant(
      final String tenantId, final RoleQuery roleQuery, final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var composedRoleQuery = buildRoleQuery(tenantId, roleQuery);
      final var result = roleServices.search(composedRoleQuery, authentication, physicalTenantId);
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
      final TenantRequest tenantRequest, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> tenantServices.updateTenant(tenantRequest, authentication, physicalTenantId),
        ResponseMapper::toTenantUpdateResponse,
        HttpStatus.OK);
  }
}
