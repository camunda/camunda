/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.search.query.GroupQuery;
import io.camunda.service.GroupServices;
import io.camunda.zeebe.gateway.protocol.rest.GroupCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.GroupUpdateRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RequestMapper.CreateGroupRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper.UpdateGroupRequest;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/groups")
public class GroupController {

  private final GroupServices groupServices;

  public GroupController(final GroupServices groupServices) {
    this.groupServices = groupServices;
  }

  @PostMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> createGroup(
      @RequestBody final GroupCreateRequest createGroupRequest) {
    return RequestMapper.toGroupCreateRequest(createGroupRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createGroup);
  }

  @PatchMapping(
      path = "/{groupKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> updateGroup(
      @PathVariable final long groupKey, @RequestBody final GroupUpdateRequest groupUpdateRequest) {
    return RequestMapper.toGroupUpdateRequest(groupUpdateRequest, groupKey)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateGroup);
  }

  @DeleteMapping(
      path = "/{groupKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CompletableFuture<ResponseEntity<Object>> deleteGroup(@PathVariable final long groupKey) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            groupServices
                .withAuthentication(RequestMapper.getAuthentication())
                .deleteGroup(groupKey));
  }

  @PostMapping(
      path = "/{groupKey}/users/{userKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CompletableFuture<ResponseEntity<Object>> assignUserToGroup(
      @PathVariable final long groupKey, @PathVariable final long userKey) {
    return RequestMapper.executeServiceMethodWithAcceptedResult(
        () ->
            groupServices
                .withAuthentication(RequestMapper.getAuthentication())
                .assignMember(groupKey, userKey, EntityType.USER));
  }

  @DeleteMapping(
      path = "/{groupKey}/users/{userKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CompletableFuture<ResponseEntity<Object>> unassignUserFromGroup(
      @PathVariable final long groupKey, @PathVariable final long userKey) {
    return RequestMapper.executeServiceMethodWithAcceptedResult(
        () ->
            groupServices
                .withAuthentication(RequestMapper.getAuthentication())
                .removeMember(groupKey, userKey, EntityType.USER));
  }

  @GetMapping(
      path = "/{groupKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<Object> getGroup(@PathVariable final long groupKey) {
    try {
      return ResponseEntity.ok()
          .body(SearchQueryResponseMapper.toGroup(groupServices.getGroup(groupKey)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<GroupSearchQueryResponse> searchGroups(
      @RequestBody(required = false) final GroupSearchQueryRequest query) {
    return SearchQueryRequestMapper.toGroupQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<GroupSearchQueryResponse> search(final GroupQuery query) {
    try {
      final var result = groupServices.search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toGroupSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> createGroup(
      final CreateGroupRequest createGroupRequest) {
    return RequestMapper.executeServiceMethod(
        () ->
            groupServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createGroup(createGroupRequest.name()),
        ResponseMapper::toGroupCreateResponse);
  }

  public CompletableFuture<ResponseEntity<Object>> updateGroup(
      final UpdateGroupRequest updateGroupRequest) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            groupServices
                .withAuthentication(RequestMapper.getAuthentication())
                .updateGroup(updateGroupRequest.groupKey(), updateGroupRequest.name()));
  }
}
