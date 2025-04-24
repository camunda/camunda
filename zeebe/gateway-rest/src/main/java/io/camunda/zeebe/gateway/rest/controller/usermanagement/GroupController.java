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
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.query.UserQuery;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberRequest;
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.protocol.rest.GroupCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.GroupUpdateRequest;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/groups")
public class GroupController {

  private final GroupServices groupServices;
  private final UserServices userServices;

  public GroupController(final GroupServices groupServices, final UserServices userServices) {
    this.groupServices = groupServices;
    this.userServices = userServices;
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
    return RequestMapper.executeServiceMethodWithAcceptedResult(
        () ->
            groupServices
                .withAuthentication(RequestMapper.getAuthentication())
                .assignMember(groupId, username, EntityType.USER));
  }

  @CamundaPutMapping(
      path = "/{groupId}/mapping-rules/{mappingId}",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> assignMappingToGroup(
      @PathVariable final String groupId, @PathVariable final String mappingId) {
    return RequestMapper.toGroupMemberRequest(groupId, mappingId, EntityType.MAPPING)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::assignMapping);
  }

  @CamundaDeleteMapping(path = "/{groupId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> unassignUserFromGroup(
      @PathVariable final String groupId, @PathVariable final String username) {
    return RequestMapper.executeServiceMethodWithAcceptedResult(
        () ->
            groupServices
                .withAuthentication(RequestMapper.getAuthentication())
                .removeMember(groupId, username, EntityType.USER));
  }

  @CamundaDeleteMapping(
      path = "/{groupId}/mapping-rules/{mappingId}",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> unassignMappingToGroup(
      @PathVariable final String groupId, @PathVariable final String mappingId) {
    return RequestMapper.toGroupMemberRequest(groupId, mappingId, EntityType.MAPPING)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::unassignMapping);
  }

  @CamundaPostMapping(path = "/{groupId}/users/search")
  public ResponseEntity<UserSearchResult> usersByGroup(
      @PathVariable final String groupId,
      @RequestBody(required = false) final UserSearchQueryRequest query) {
    return SearchQueryRequestMapper.toUserQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            userQuery -> searchUsersInGroup(groupId, userQuery));
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

  public CompletableFuture<ResponseEntity<Object>> assignMapping(final GroupMemberRequest request) {
    return RequestMapper.executeServiceMethodWithAcceptedResult(
        () ->
            groupServices
                .withAuthentication(RequestMapper.getAuthentication())
                .assignMember(request.groupId(), request.entityId(), request.entityType()));
  }

  private ResponseEntity<UserSearchResult> searchUsersInGroup(
      final String groupId, final UserQuery userQuery) {
    try {
      final var composedUserQuery = buildUserQuery(groupId, userQuery);
      final var result =
          userServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(composedUserQuery);
      return ResponseEntity.ok(SearchQueryResponseMapper.toUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private UserQuery buildUserQuery(final String groupId, final UserQuery userQuery) {
    return userQuery.toBuilder()
        .filter(userQuery.filter().toBuilder().groupId(groupId).build())
        .build();
  }

  public CompletableFuture<ResponseEntity<Object>> unassignMapping(
      final GroupMemberRequest request) {
    return RequestMapper.executeServiceMethodWithAcceptedResult(
        () ->
            groupServices
                .withAuthentication(RequestMapper.getAuthentication())
                .removeMember(request.groupId(), request.entityId(), request.entityType()));
  }
}
