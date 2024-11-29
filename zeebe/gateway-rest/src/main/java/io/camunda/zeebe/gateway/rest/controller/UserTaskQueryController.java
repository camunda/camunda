/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.entities.FormEntity;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.service.UserTaskServices;
import io.camunda.zeebe.gateway.protocol.rest.FormItem;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskItem;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskVariableSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.VariableSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.util.XmlUtil;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestQueryController
@RequestMapping("/v2/user-tasks")
public class UserTaskQueryController {

  private final UserTaskServices userTaskServices;
  private final XmlUtil xmlUtil;

  public UserTaskQueryController(final UserTaskServices userTaskServices, final XmlUtil xmlUtil) {
    this.userTaskServices = userTaskServices;
    this.xmlUtil = xmlUtil;
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<UserTaskSearchQueryResponse> searchUserTasks(
      @RequestBody(required = false) final UserTaskSearchQueryRequest query) {
    return SearchQueryRequestMapper.toUserTaskQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<UserTaskSearchQueryResponse> search(final UserTaskQuery query) {
    try {
      final var result =
          userTaskServices.withAuthentication(RequestMapper.getAuthentication()).search(query);
      final var nameMap = xmlUtil.getUserTasksNames(result.items());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toUserTaskSearchQueryResponse(result, nameMap));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @GetMapping(
      path = "/{userTaskKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<UserTaskItem> getByKey(
      @PathVariable("userTaskKey") final Long userTaskKey) {
    try {
      final var userTask =
          userTaskServices
              .withAuthentication(RequestMapper.getAuthentication())
              .getByKey(userTaskKey);
      final var name = xmlUtil.getUserTaskName(userTask);
      return ResponseEntity.ok().body(SearchQueryResponseMapper.toUserTask(userTask, name));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @GetMapping(
      path = "/{userTaskKey}/form",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<FormItem> getFormByUserTaskKey(
      @PathVariable("userTaskKey") final long userTaskKey) {
    try {
      final Optional<FormEntity> form =
          userTaskServices
              .withAuthentication(RequestMapper.getAuthentication())
              .getUserTaskForm(userTaskKey);
      return form.map(SearchQueryResponseMapper::toFormItem)
          .map(ResponseEntity::ok)
          .orElseGet(() -> ResponseEntity.noContent().build());
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @PostMapping(
      path = "/{userTaskKey}/variables",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<VariableSearchQueryResponse> searchVariables(
      @PathVariable("userTaskKey") final long userTaskKey,
      @RequestBody(required = false)
          final UserTaskVariableSearchQueryRequest userTaskVariablesSearchQueryRequest) {
    return SearchQueryRequestMapper.toUserTaskVariableQuery(userTaskVariablesSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> searchUserTaskVariableQuery(userTaskKey, query));
  }

  private ResponseEntity<VariableSearchQueryResponse> searchUserTaskVariableQuery(
      final long userTaskKey, final VariableQuery query) {
    try {
      final var result =
          userTaskServices
              .withAuthentication(RequestMapper.getAuthentication())
              .searchUserTaskVariables(userTaskKey, query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toVariableSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
