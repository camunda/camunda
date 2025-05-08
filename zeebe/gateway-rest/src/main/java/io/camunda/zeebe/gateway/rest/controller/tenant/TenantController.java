/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.tenant;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.MappingQuery;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.service.MappingServices;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.protocol.rest.MappingSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.MappingSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantUpdateRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResult;
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
import org.springframework.web.bind.annotation.*;

@CamundaRestController
@RequestMapping("/v2/tenants")
public class TenantController {
  private final TenantServices tenantServices;
  private final UserServices userServices;
  private final MappingServices mappingServices;

  public TenantController(
      final TenantServices tenantServices,
      final UserServices userServices,
      final MappingServices mappingServices) {
    this.tenantServices = tenantServices;
    this.userServices = userServices;
    this.mappingServices = mappingServices;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createTenant(
      @RequestBody final TenantCreateRequest createTenantRequest) {
    return RequestMapper.toTenantCreateDto(createTenantRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createTenant);
  }

  @CamundaGetMapping(path = "/{tenantId}")
  public ResponseEntity<TenantResult> getTenant(@PathVariable final String tenantId) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toTenant(
                  tenantServices
                      .withAuthentication(RequestMapper.getAuthentication())
                      .getById(tenantId)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

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
  public CompletableFuture<ResponseEntity<Object>> assignUsersToTenant(
      @PathVariable final String tenantId, @PathVariable final String username) {
    return RequestMapper.toTenantMemberRequest(tenantId, username, EntityType.USER)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::addMemberToTenant);
  }

  @CamundaPostMapping(path = "/{tenantId}/users/search")
  public ResponseEntity<UserSearchResult> searchUsersInTenant(
      @PathVariable final String tenantId,
      @RequestBody(required = false) final UserSearchQueryRequest query) {
    return SearchQueryRequestMapper.toUserQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            userQuery -> searchUsersInTenant(tenantId, userQuery));
  }

  @CamundaPutMapping(path = "/{tenantId}/applications/{applicationId}")
  public CompletableFuture<ResponseEntity<Object>> assignApplicationToTenant(
      @PathVariable final String tenantId, @PathVariable final String applicationId) {
    return RequestMapper.toTenantMemberRequest(tenantId, applicationId, EntityType.APPLICATION)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::addMemberToTenant);
  }

  @CamundaPutMapping(path = "/{tenantId}/mapping-rules/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> assignMappingToTenant(
      @PathVariable final String tenantId, @PathVariable final String mappingRuleId) {
    return RequestMapper.toTenantMemberRequest(tenantId, mappingRuleId, EntityType.MAPPING)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::addMemberToTenant);
  }

  @CamundaPutMapping(path = "/{tenantId}/groups/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> assignGroupToTenant(
      @PathVariable final String tenantId, @PathVariable final String groupId) {
    return RequestMapper.toTenantMemberRequest(tenantId, groupId, EntityType.GROUP)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::addMemberToTenant);
  }

  @CamundaPutMapping(path = "/{tenantId}/roles/{roleId}")
  public CompletableFuture<ResponseEntity<Object>> assignRoleToTenant(
      @PathVariable final String tenantId, @PathVariable final String roleId) {
    return RequestMapper.toTenantMemberRequest(tenantId, roleId, EntityType.ROLE)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::addMemberToTenant);
  }

  @CamundaDeleteMapping(path = "/{tenantId}")
  public CompletableFuture<ResponseEntity<Object>> deleteTenant(
      @PathVariable final String tenantId) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            tenantServices
                .withAuthentication(RequestMapper.getAuthentication())
                .deleteTenant(tenantId));
  }

  @CamundaDeleteMapping(path = "/{tenantId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> removeUserFromTenant(
      @PathVariable final String tenantId, @PathVariable final String username) {
    return RequestMapper.toTenantMemberRequest(tenantId, username, EntityType.USER)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::removeMemberFromTenant);
  }

  @CamundaDeleteMapping(path = "/{tenantId}/applications/{applicationId}")
  public CompletableFuture<ResponseEntity<Object>> removeApplicationFromTenant(
      @PathVariable final String tenantId, @PathVariable final String applicationId) {
    return RequestMapper.toTenantMemberRequest(tenantId, applicationId, EntityType.APPLICATION)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::removeMemberFromTenant);
  }

  @CamundaPostMapping(path = "/{tenantId}/mapping-rules/search")
  public ResponseEntity<MappingSearchQueryResult> searchMappingsInTenant(
      @PathVariable final String tenantId,
      @RequestBody(required = false) final MappingSearchQueryRequest query) {
    return SearchQueryRequestMapper.toMappingQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mappingQuery -> searchMappingsInTenant(tenantId, mappingQuery));
  }

  @CamundaDeleteMapping(path = "/{tenantId}/mapping-rules/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> removeMappingFromTenant(
      @PathVariable final String tenantId, @PathVariable final String mappingRuleId) {
    return RequestMapper.toTenantMemberRequest(tenantId, mappingRuleId, EntityType.MAPPING)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::removeMemberFromTenant);
  }

  @CamundaDeleteMapping(path = "/{tenantId}/groups/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> removeGroupFromTenant(
      @PathVariable final String tenantId, @PathVariable final String groupId) {
    return RequestMapper.toTenantMemberRequest(tenantId, groupId, EntityType.GROUP)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::removeMemberFromTenant);
  }

  @CamundaDeleteMapping(path = "/{tenantId}/roles/{roleId}")
  public CompletableFuture<ResponseEntity<Object>> removeRoleFromTenant(
      @PathVariable final String tenantId, @PathVariable final String roleId) {
    return RequestMapper.toTenantMemberRequest(tenantId, roleId, EntityType.ROLE)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::removeMemberFromTenant);
  }

  private CompletableFuture<ResponseEntity<Object>> createTenant(final TenantDTO tenantDTO) {
    return RequestMapper.executeServiceMethod(
        () ->
            tenantServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createTenant(tenantDTO),
        ResponseMapper::toTenantCreateResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> addMemberToTenant(
      final TenantMemberRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            tenantServices
                .withAuthentication(RequestMapper.getAuthentication())
                .addMember(request));
  }

  private CompletableFuture<ResponseEntity<Object>> removeMemberFromTenant(
      final TenantMemberRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            tenantServices
                .withAuthentication(RequestMapper.getAuthentication())
                .removeMember(request));
  }

  private ResponseEntity<TenantSearchQueryResult> search(final TenantQuery query) {
    try {
      final var result =
          tenantServices.withAuthentication(RequestMapper.getAuthentication()).search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toTenantSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<MappingSearchQueryResult> searchMappingsInTenant(
      final String tenantId, final MappingQuery mappingQuery) {
    try {
      final var composedMappingQuery = buildMappingQuery(tenantId, mappingQuery);
      final var result =
          mappingServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(composedMappingQuery);
      return ResponseEntity.ok(SearchQueryResponseMapper.toMappingSearchQueryResponse(result));
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
              .withAuthentication(RequestMapper.getAuthentication())
              .search(composedUserQuery);
      return ResponseEntity.ok(SearchQueryResponseMapper.toUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private MappingQuery buildMappingQuery(final String tenantId, final MappingQuery mappingQuery) {
    return mappingQuery.toBuilder()
        .filter(mappingQuery.filter().toBuilder().tenantId(tenantId).build())
        .build();
  }

  private UserQuery buildUserQuery(final String tenantId, final UserQuery userQuery) {
    return userQuery.toBuilder()
        .filter(userQuery.filter().toBuilder().tenantId(tenantId).build())
        .build();
  }

  private CompletableFuture<ResponseEntity<Object>> updateTenant(final TenantDTO tenantDTO) {
    return RequestMapper.executeServiceMethod(
        () ->
            tenantServices
                .withAuthentication(RequestMapper.getAuthentication())
                .updateTenant(tenantDTO),
        ResponseMapper::toTenantUpdateResponse);
  }
}
