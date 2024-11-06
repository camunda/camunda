/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.Loggers.REST_LOGGER;
import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.service.FlowNodeInstanceServices;
import io.camunda.service.FormServices;
import io.camunda.service.UserTaskServices;
import io.camunda.service.VariableServices;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskVariableSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.VariableSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import jakarta.validation.ValidationException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
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
  private final VariableServices variableServices;
  private final FlowNodeInstanceServices flowNodeInstanceServices;
  private final FormServices formServices;

  public UserTaskQueryController(
      final UserTaskServices userTaskServices,
      final FormServices formServices,
      final VariableServices variableServices,
      final FlowNodeInstanceServices flowNodeInstanceServices) {
    this.userTaskServices = userTaskServices;
    this.formServices = formServices;
    this.variableServices = variableServices;
    this.flowNodeInstanceServices = flowNodeInstanceServices;
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> searchUserTasks(
      @RequestBody(required = false) final UserTaskSearchQueryRequest query) {
    return SearchQueryRequestMapper.toUserTaskQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<Object> search(final UserTaskQuery query) {
    try {
      final var result =
          userTaskServices.withAuthentication(RequestMapper.getAuthentication()).search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toUserTaskSearchQueryResponse(result));
    } catch (final ValidationException e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Validation failed for UserTask Search Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    } catch (final Exception e) {
      REST_LOGGER.debug("An exception occurred in searchUserTasks.", e);
      return mapErrorToResponse(e);
    }
  }

  @GetMapping(
      path = "/{userTaskKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<Object> getByKey(@PathVariable("userTaskKey") final Long userTaskKey) {
    try {
      // Success case: Return the left side with the UserTaskItem wrapped in ResponseEntity
      return ResponseEntity.ok()
          .body(SearchQueryResponseMapper.toUserTask(userTaskServices.getByKey(userTaskKey)));
    } catch (final Exception e) {
      REST_LOGGER.debug("An exception occurred in getUserTask.", e);
      return mapErrorToResponse(e);
    }
  }

  @GetMapping(
      path = "/{userTaskKey}/form",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<Object> getFormByUserTaskKey(
      @PathVariable("userTaskKey") final Long userTaskKey) {
    try {
      final Long formKey = userTaskServices.getByKey(userTaskKey).formKey();

      if (formKey == null) {
        return ResponseEntity.noContent().build();
      }

      return ResponseEntity.ok()
          .body(SearchQueryResponseMapper.toFormItem(formServices.getByKey(formKey)));
    } catch (final Exception e) {
      REST_LOGGER.debug("An exception occurred in getUserTaskForm.", e);
      return mapErrorToResponse(e);
    }
  }

  @PostMapping(
      path = "/{userTaskKey}/variables",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<VariableSearchQueryResponse> searchVariables(
      @PathVariable("userTaskKey") final Long userTaskKey,
      @RequestBody(required = false)
          final UserTaskVariableSearchQueryRequest userTaskVariablesSearchQueryRequest) {
    // Retrieve user tak data
    final var userTask = userTaskServices.getByKey(userTaskKey);

    // Retrieve treePath for flowNodeInstanceId
    final var flowNodeInstance = flowNodeInstanceServices.getByKey(userTask.flowNodeInstanceId());

    final var treePath = flowNodeInstance.treePath();
    final List<Long> treePathList =
        (treePath != null && !treePath.isEmpty())
            ? Arrays.stream(treePath.split("/")).map(Long::valueOf).collect(Collectors.toList())
            : Collections.emptyList();

    return SearchQueryRequestMapper.toUserTaskVariableQuery(
            userTaskVariablesSearchQueryRequest, treePathList)
        .fold(RestErrorMapper::mapProblemToResponse, this::searchUserTaskVariableQuery);
  }

  private ResponseEntity<VariableSearchQueryResponse> searchUserTaskVariableQuery(
      final VariableQuery query) {
    try {
      final var result =
          variableServices.withAuthentication(RequestMapper.getAuthentication()).search(query);

      return ResponseEntity.ok(SearchQueryResponseMapper.toVariableSearchQueryResponse(result));
    } catch (final Exception e) {
      REST_LOGGER.debug("An exception occurred in searchUserTaskVariables.", e);
      return mapErrorToResponse(e);
    }
  }
}
