/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;
import static io.camunda.zeebe.protocol.record.value.EntityType.GROUP;

import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.MappingQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberDTO;
import io.camunda.service.MappingServices;
import io.camunda.service.RoleServices;
import io.camunda.zeebe.gateway.protocol.rest.GroupCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.GroupUpdateRequest;
import io.camunda.zeebe.gateway.protocol.rest.GroupUserSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.GroupUserSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.MappingSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.MappingSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.RoleSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleSearchQueryResult;
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
@RequestMapping("/v2/groups")
public class GroupController {

  private final GroupServices groupServices;
  private final MappingServices mappingServices;
  private final RoleServices roleServices;

  public GroupController(
      final GroupServices groupServices,
      final MappingServices mappingServices,
      final RoleServices roleServices) {
    this.groupServices = groupServices;
    this.mappingServices = mappingServices;
    this.roleServices = roleServices;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createGroup(
      @RequestBody final GroupCreateRequest createGroupRequest) {
    return RequestMapper.toGroupCreateRequest(createGroupRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createGroup);
  }

  @CamundaPutMapping(path = "/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> updateGroup(
      @PathVariable final String groupId,
      @RequestBody final GroupUpdateRequest groupUpdateRequest) {
    return RequestMapper.toGroupUpdateRequest(groupUpdateRequest, groupId)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateGroup);
  }

  @CamundaDeleteMapping(path = "/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> deleteGroup(@PathVariable final String groupId) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            groupServices
                .withAuthentication(RequestMapper.getAuthentication())
                .deleteGroup(groupId));
  }

  @CamundaPutMapping(
      path = "/{groupId}/users/{username}",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> assignUserToGroup(
      @PathVariable final String groupId, @PathVariable final String username) {
    return RequestMapper.toGroupMemberRequest(groupId, username, EntityType.USER)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::assignMember);
  }

  @CamundaPutMapping(
      path = "/{groupId}/clients/{clientId}",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> assignApplicationToGroup(
      @PathVariable final String groupId, @PathVariable final String clientId) {
    return RequestMapper.toGroupMemberRequest(groupId, clientId, EntityType.CLIENT)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::assignMember);
  }

  @CamundaPutMapping(
      path = "/{groupId}/mapping-rules/{mappingId}",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> assignMappingToGroup(
      @PathVariable final String groupId, @PathVariable final String mappingId) {
    return RequestMapper.toGroupMemberRequest(groupId, mappingId, EntityType.MAPPING)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::assignMember);
  }

  @CamundaDeleteMapping(path = "/{groupId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> unassignUserFromGroup(
      @PathVariable final String groupId, @PathVariable final String username) {
    return RequestMapper.toGroupMemberRequest(groupId, username, EntityType.USER)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::unassignMember);
  }

  @CamundaDeleteMapping(path = "/{groupId}/clients/{clientId}")
  public CompletableFuture<ResponseEntity<Object>> unassignApplicationFromGroup(
      @PathVariable final String groupId, @PathVariable final String clientId) {
    return RequestMapper.toGroupMemberRequest(groupId, clientId, EntityType.CLIENT)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::unassignMember);
  }

  @CamundaDeleteMapping(
      path = "/{groupId}/mapping-rules/{mappingId}",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> unassignMappingToGroup(
      @PathVariable final String groupId, @PathVariable final String mappingId) {
    return RequestMapper.toGroupMemberRequest(groupId, mappingId, EntityType.MAPPING)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::unassignMember);
  }

  @CamundaPostMapping(path = "/{groupId}/users/search")
  public ResponseEntity<GroupUserSearchResult> usersByGroup(
      @PathVariable final String groupId,
      @RequestBody(required = false) final GroupUserSearchQueryRequest query) {
    return SearchQueryRequestMapper.toGroupQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            groupQuery -> searchUsersInGroup(groupId, groupQuery));
  }

  @CamundaPostMapping(path = "/{groupId}/mapping-rules/search")
  public ResponseEntity<MappingSearchQueryResult> mappingsByGroup(
      @PathVariable final String groupId,
      @RequestBody(required = false) final MappingSearchQueryRequest query) {
    return SearchQueryRequestMapper.toMappingQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mappingQuery -> searchMappingsInGroup(groupId, mappingQuery));
  }

  @CamundaPostMapping(path = "/{groupId}/roles/search")
  public ResponseEntity<RoleSearchQueryResult> rolesByGroup(
      @PathVariable final String groupId,
      @RequestBody(required = false) final RoleSearchQueryRequest query) {
    return SearchQueryRequestMapper.toRoleQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            userQuery -> searchRolesInGroup(groupId, userQuery));
  }

  @CamundaGetMapping(path = "/{groupId}")
  public ResponseEntity<Object> getGroup(@PathVariable final String groupId) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toGroup(
                  groupServices
                      .withAuthentication(RequestMapper.getAuthentication())
                      .getGroup(groupId)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<GroupSearchQueryResult> searchGroups(
      @RequestBody(required = false) final GroupSearchQueryRequest query) {
    return SearchQueryRequestMapper.toGroupQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<GroupSearchQueryResult> search(final GroupQuery query) {
    try {
      final var result =
          groupServices.withAuthentication(RequestMapper.getAuthentication()).search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toGroupSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> createGroup(final GroupDTO groupDTO) {
    return RequestMapper.executeServiceMethod(
        () ->
            groupServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createGroup(groupDTO),
        ResponseMapper::toGroupCreateResponse);
  }

  public CompletableFuture<ResponseEntity<Object>> updateGroup(final GroupDTO updateGroupRequest) {
    return RequestMapper.executeServiceMethod(
        () ->
            groupServices
                .withAuthentication(RequestMapper.getAuthentication())
                .updateGroup(
                    updateGroupRequest.groupId(),
                    updateGroupRequest.name(),
                    updateGroupRequest.description()),
        ResponseMapper::toGroupUpdateResponse);
  }

  public CompletableFuture<ResponseEntity<Object>> assignMember(final GroupMemberDTO request) {
    return RequestMapper.executeServiceMethodWithAcceptedResult(
        () ->
            groupServices
                .withAuthentication(RequestMapper.getAuthentication())
                .assignMember(request));
  }

  private ResponseEntity<GroupUserSearchResult> searchUsersInGroup(
      final String groupId, final GroupQuery groupQuery) {
    try {
      final var result =
          groupServices
              .withAuthentication(RequestMapper.getAuthentication())
              .searchMembers(buildGroupMemberQuery(groupId, EntityType.USER, groupQuery));
      return ResponseEntity.ok(SearchQueryResponseMapper.toGroupUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<MappingSearchQueryResult> searchMappingsInGroup(
      final String groupId, final MappingQuery mappingQuery) {
    try {
      final var composedMappingQuery = buildMappingQuery(groupId, mappingQuery);
      final var result =
          mappingServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(composedMappingQuery);
      return ResponseEntity.ok(SearchQueryResponseMapper.toMappingSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<RoleSearchQueryResult> searchRolesInGroup(
      final String groupId, final RoleQuery roleQuery) {
    try {
      final var composedRoleQuery = buildRoleQuery(groupId, roleQuery);
      final var result =
          roleServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(composedRoleQuery);
      return ResponseEntity.ok(SearchQueryResponseMapper.toRoleSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private GroupQuery buildGroupMemberQuery(
      final String groupId, final EntityType memberType, final GroupQuery groupQuery) {
    return groupQuery.toBuilder()
        .filter(
            groupQuery.filter().toBuilder().joinParentId(groupId).memberType(memberType).build())
        .build();
  }

  private MappingQuery buildMappingQuery(final String groupId, final MappingQuery mappingQuery) {
    return mappingQuery.toBuilder()
        .filter(mappingQuery.filter().toBuilder().groupId(groupId).build())
        .build();
  }

  private RoleQuery buildRoleQuery(final String groupId, final RoleQuery roleQuery) {
    return roleQuery.toBuilder()
        .filter(roleQuery.filter().toBuilder().memberId(groupId).childMemberType(GROUP).build())
        .build();
  }

  public CompletableFuture<ResponseEntity<Object>> unassignMember(final GroupMemberDTO request) {
    return RequestMapper.executeServiceMethodWithAcceptedResult(
        () ->
            groupServices
                .withAuthentication(RequestMapper.getAuthentication())
                .removeMember(request));
  }
}
