/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.tenant;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.TenantMemberQuery;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.service.TenantServices.TenantRequest;
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.RoleSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantClientSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantClientSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantGroupSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantGroupSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantUpdateRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantUserSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantUserSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResult;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.gateway.rest.mapper.RequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.ResponseMapper;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryResponseMapper;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CamundaRestController
@RequestMapping("/v2/tenants")
public class TenantController {
  private final TenantServices tenantServices;
  private final UserServices userServices;
  private final MappingRuleServices mappingRuleServices;
  private final GroupServices groupServices;
  private final RoleServices roleServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final SecurityConfiguration securityConfiguration;

  public TenantController(
      final TenantServices tenantServices,
      final UserServices userServices,
      final MappingRuleServices mappingRuleServices,
      final GroupServices groupServices,
      final RoleServices roleServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final SecurityConfiguration securityConfiguration) {
    this.tenantServices = tenantServices;
    this.userServices = userServices;
    this.mappingRuleServices = mappingRuleServices;
    this.groupServices = groupServices;
    this.roleServices = roleServices;
    this.authenticationProvider = authenticationProvider;
    this.securityConfiguration = securityConfiguration;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createTenant(
      @RequestBody final TenantCreateRequest createTenantRequest) {
    return RequestMapper.toTenantCreateDto(createTenantRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createTenant);
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{tenantId}")
  public ResponseEntity<TenantResult> getTenant(@PathVariable final String tenantId) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toTenant(
                  tenantServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getById(tenantId)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<TenantSearchQueryResult> searchTenants(
      @RequestBody(required = false) final TenantSearchQueryRequest query) {
    return SearchQueryRequestMapper.toTenantQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @CamundaPutMapping(path = "/{tenantId}")
  public CompletableFuture<ResponseEntity<Object>> updateTenant(
      @PathVariable final String tenantId,
      @RequestBody final TenantUpdateRequest tenantUpdateRequest) {
    return RequestMapper.toTenantUpdateDto(tenantId, tenantUpdateRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateTenant);
  }

  @CamundaPutMapping(path = "/{tenantId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> assignUserToTenant(
      @PathVariable final String tenantId, @PathVariable final String username) {
    return RequestMapper.toTenantMemberRequest(
            tenantId,
            username,
            EntityType.USER,
            securityConfiguration.getCompiledIdValidationPattern())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::addMemberToTenant);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{tenantId}/users/search")
  public ResponseEntity<TenantUserSearchResult> searchUsersInTenant(
      @PathVariable final String tenantId,
      @RequestBody(required = false) final TenantUserSearchQueryRequest query) {
    return SearchQueryRequestMapper.toTenantMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            tenantQuery -> searchUsersInTenant(tenantId, tenantQuery));
  }

  @CamundaPutMapping(path = "/{tenantId}/clients/{clientId}")
  public CompletableFuture<ResponseEntity<Object>> assignClientToTenant(
      @PathVariable final String tenantId, @PathVariable final String clientId) {
    return RequestMapper.toTenantMemberRequest(
            tenantId,
            clientId,
            EntityType.CLIENT,
            securityConfiguration.getCompiledIdValidationPattern())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::addMemberToTenant);
  }

  @CamundaPutMapping(path = "/{tenantId}/mapping-rules/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> assignMappingRuleToTenant(
      @PathVariable final String tenantId, @PathVariable final String mappingRuleId) {
    return RequestMapper.toTenantMemberRequest(
            tenantId,
            mappingRuleId,
            EntityType.MAPPING_RULE,
            securityConfiguration.getCompiledIdValidationPattern())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::addMemberToTenant);
  }

  @CamundaPutMapping(path = "/{tenantId}/groups/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> assignGroupToTenant(
      @PathVariable final String tenantId, @PathVariable final String groupId) {
    return RequestMapper.toTenantMemberRequest(
            tenantId,
            groupId,
            EntityType.GROUP,
            securityConfiguration.getCompiledIdValidationPattern())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::addMemberToTenant);
  }

  @CamundaPutMapping(path = "/{tenantId}/roles/{roleId}")
  public CompletableFuture<ResponseEntity<Object>> assignRoleToTenant(
      @PathVariable final String tenantId, @PathVariable final String roleId) {
    return RequestMapper.toTenantMemberRequest(
            tenantId,
            roleId,
            EntityType.ROLE,
            securityConfiguration.getCompiledIdValidationPattern())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::addMemberToTenant);
  }

  @CamundaDeleteMapping(path = "/{tenantId}")
  public CompletableFuture<ResponseEntity<Object>> deleteTenant(
      @PathVariable final String tenantId) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            tenantServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .deleteTenant(tenantId));
  }

  @CamundaDeleteMapping(path = "/{tenantId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> unassignUserFromTenant(
      @PathVariable final String tenantId, @PathVariable final String username) {
    return RequestMapper.toTenantMemberRequest(
            tenantId,
            username,
            EntityType.USER,
            securityConfiguration.getCompiledIdValidationPattern())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::removeMemberFromTenant);
  }

  @CamundaDeleteMapping(path = "/{tenantId}/clients/{clientId}")
  public CompletableFuture<ResponseEntity<Object>> unassignClientFromTenant(
      @PathVariable final String tenantId, @PathVariable final String clientId) {
    return RequestMapper.toTenantMemberRequest(
            tenantId,
            clientId,
            EntityType.CLIENT,
            securityConfiguration.getCompiledIdValidationPattern())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::removeMemberFromTenant);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{tenantId}/mapping-rules/search")
  public ResponseEntity<MappingRuleSearchQueryResult> searchMappingRulesForTenant(
      @PathVariable final String tenantId,
      @RequestBody(required = false) final MappingRuleSearchQueryRequest query) {
    return SearchQueryRequestMapper.toMappingRuleQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mappingRuleQuery -> searchMappingRulesForTenant(tenantId, mappingRuleQuery));
  }

  @CamundaDeleteMapping(path = "/{tenantId}/mapping-rules/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> unassignMappingRuleFromTenant(
      @PathVariable final String tenantId, @PathVariable final String mappingRuleId) {
    return RequestMapper.toTenantMemberRequest(
            tenantId,
            mappingRuleId,
            EntityType.MAPPING_RULE,
            securityConfiguration.getCompiledIdValidationPattern())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::removeMemberFromTenant);
  }

  @CamundaDeleteMapping(path = "/{tenantId}/groups/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> unassignGroupFromTenant(
      @PathVariable final String tenantId, @PathVariable final String groupId) {
    return RequestMapper.toTenantMemberRequest(
            tenantId,
            groupId,
            EntityType.GROUP,
            securityConfiguration.getCompiledIdValidationPattern())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::removeMemberFromTenant);
  }

  @CamundaDeleteMapping(path = "/{tenantId}/roles/{roleId}")
  public CompletableFuture<ResponseEntity<Object>> unassignRoleFromTenant(
      @PathVariable final String tenantId, @PathVariable final String roleId) {
    return RequestMapper.toTenantMemberRequest(
            tenantId,
            roleId,
            EntityType.ROLE,
            securityConfiguration.getCompiledIdValidationPattern())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::removeMemberFromTenant);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{tenantId}/groups/search")
  public ResponseEntity<TenantGroupSearchResult> searchGroupIdsInTenant(
      @PathVariable final String tenantId,
      @RequestBody(required = false) final TenantGroupSearchQueryRequest query) {
    return SearchQueryRequestMapper.toTenantMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            tenantQuery -> searchGroupIdsInTenant(tenantId, tenantQuery));
  }

  private ResponseEntity<TenantGroupSearchResult> searchGroupIdsInTenant(
      final String tenantId, final TenantMemberQuery query) {
    try {
      final var result =
          tenantServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .searchMembers(buildTenantMemberQuery(tenantId, EntityType.GROUP, query));
      return ResponseEntity.ok(SearchQueryResponseMapper.toTenantGroupSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{tenantId}/roles/search")
  public ResponseEntity<RoleSearchQueryResult> searchRolesInTenant(
      @PathVariable final String tenantId,
      @RequestBody(required = false) final RoleSearchQueryRequest query) {
    return SearchQueryRequestMapper.toRoleQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            roleQuery -> searchRolesInTenant(tenantId, roleQuery));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{tenantId}/clients/search")
  public ResponseEntity<TenantClientSearchResult> searchClientsInTenant(
      @PathVariable final String tenantId,
      @RequestBody(required = false) final TenantClientSearchQueryRequest query) {
    return SearchQueryRequestMapper.toTenantMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            tenantQuery -> searchClientsInTenant(tenantId, tenantQuery));
  }

  private CompletableFuture<ResponseEntity<Object>> createTenant(
      final TenantRequest tenantRequest) {
    return RequestMapper.executeServiceMethod(
        () ->
            tenantServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createTenant(tenantRequest),
        ResponseMapper::toTenantCreateResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> addMemberToTenant(
      final TenantMemberRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            tenantServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .addMember(request));
  }

  private CompletableFuture<ResponseEntity<Object>> removeMemberFromTenant(
      final TenantMemberRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            tenantServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .removeMember(request));
  }

  private ResponseEntity<TenantSearchQueryResult> search(final TenantQuery query) {
    try {
      final var result =
          tenantServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toTenantSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<TenantUserSearchResult> searchUsersInTenant(
      final String tenantId, final TenantMemberQuery tenantMemberQuery) {
    try {
      final var result =
          tenantServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .searchMembers(buildTenantMemberQuery(tenantId, EntityType.USER, tenantMemberQuery));
      return ResponseEntity.ok(SearchQueryResponseMapper.toTenantUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<TenantClientSearchResult> searchClientsInTenant(
      final String tenantId, final TenantMemberQuery query) {
    try {
      final var result =
          tenantServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .searchMembers(buildTenantMemberQuery(tenantId, EntityType.CLIENT, query));
      return ResponseEntity.ok(SearchQueryResponseMapper.toTenantClientSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<MappingRuleSearchQueryResult> searchMappingRulesForTenant(
      final String tenantId, final MappingRuleQuery mappingRuleQuery) {
    try {
      final var composedMappingRuleQuery = buildMappingRuleQuery(tenantId, mappingRuleQuery);
      final var result =
          mappingRuleServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(composedMappingRuleQuery);
      return ResponseEntity.ok(SearchQueryResponseMapper.toMappingRuleSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<UserSearchResult> searchUsersInTenant(
      final String tenantId, final UserQuery userQuery) {
    try {
      final var composedUserQuery = buildUserQuery(tenantId, userQuery);
      final var result =
          userServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(composedUserQuery);
      return ResponseEntity.ok(SearchQueryResponseMapper.toUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<GroupSearchQueryResult> searchGroupsInTenant(
      final String tenantId, final GroupQuery groupQuery) {
    try {
      final var composedGroupQuery = buildGroupQuery(tenantId, groupQuery);
      final var result =
          groupServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(composedGroupQuery);
      return ResponseEntity.ok(SearchQueryResponseMapper.toGroupSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<RoleSearchQueryResult> searchRolesInTenant(
      final String tenantId, final RoleQuery roleQuery) {
    try {
      final var composedRoleQuery = buildRoleQuery(tenantId, roleQuery);
      final var result =
          roleServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(composedRoleQuery);
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
      final TenantRequest tenantRequest) {
    return RequestMapper.executeServiceMethod(
        () ->
            tenantServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .updateTenant(tenantRequest),
        ResponseMapper::toTenantUpdateResponse);
  }
}
