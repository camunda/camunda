/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.UserQuery;
import io.camunda.service.GroupServices;
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResponse;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/groups")
public class GroupQueryController {

  private final GroupServices groupServices;
  private final UserServices userServices;

  public GroupQueryController(final GroupServices groupServices, final UserServices userServices) {
    this.groupServices = groupServices;
    this.userServices = userServices;
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

  @PostMapping(
      path = "/{groupKey}/users/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<UserSearchResponse> searchUsersInGroup(
      @PathVariable final long groupKey,
      @RequestBody(required = false) final UserSearchQueryRequest query) {
    final var groupMemberKeys =
        groupServices
            .search(
                SearchQueryBuilders.groupSearchQuery().filter(f -> f.groupKey(groupKey)).build())
            .items()
            .stream()
            .map(GroupEntity::assignedMemberKeys)
            .flatMap(Set::stream)
            .collect(Collectors.toSet());
    return SearchQueryRequestMapper.toUserQuery(query, groupMemberKeys)
        .fold(RestErrorMapper::mapProblemToResponse, this::searchUsersInGroup);
  }

  private ResponseEntity<GroupSearchQueryResponse> search(final GroupQuery query) {
    try {
      final var result = groupServices.search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toGroupSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  private ResponseEntity<UserSearchResponse> searchUsersInGroup(final UserQuery query) {
    try {
      final var result = userServices.search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }
}
