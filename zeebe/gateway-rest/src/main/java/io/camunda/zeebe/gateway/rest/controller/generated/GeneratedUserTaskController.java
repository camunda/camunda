/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskAssignmentRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskAuditLogSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskCompletionRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskUpdateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskVariableSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequestMapping(path = {"/v1", "/v2"})
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public class GeneratedUserTaskController {

  private final UserTaskServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedUserTaskController(
      final UserTaskServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/user-tasks/{userTaskKey}/completion",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> completeUserTask(
      @PathVariable("userTaskKey") final Long userTaskKey,
      @RequestBody(required = false) final GeneratedUserTaskCompletionRequestStrictContract userTaskCompletionRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.completeUserTask(userTaskKey, userTaskCompletionRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/user-tasks/{userTaskKey}/assignment",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> assignUserTask(
      @PathVariable("userTaskKey") final Long userTaskKey,
      @RequestBody final GeneratedUserTaskAssignmentRequestStrictContract userTaskAssignmentRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.assignUserTask(userTaskKey, userTaskAssignmentRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/user-tasks/{userTaskKey}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> getUserTask(
      @PathVariable("userTaskKey") final Long userTaskKey
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getUserTask(userTaskKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PATCH,
      value = "/user-tasks/{userTaskKey}",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> updateUserTask(
      @PathVariable("userTaskKey") final Long userTaskKey,
      @RequestBody(required = false) final GeneratedUserTaskUpdateRequestStrictContract userTaskUpdateRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.updateUserTask(userTaskKey, userTaskUpdateRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/user-tasks/{userTaskKey}/form",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> getUserTaskForm(
      @PathVariable("userTaskKey") final Long userTaskKey
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getUserTaskForm(userTaskKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/user-tasks/{userTaskKey}/assignee",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> unassignUserTask(
      @PathVariable("userTaskKey") final Long userTaskKey
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.unassignUserTask(userTaskKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/user-tasks/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchUserTasks(
      @RequestBody(required = false) final GeneratedUserTaskSearchQueryRequestStrictContract userTaskSearchQuery
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchUserTasks(userTaskSearchQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/user-tasks/{userTaskKey}/variables/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchUserTaskVariables(
      @PathVariable("userTaskKey") final Long userTaskKey,
      @RequestParam(name = "truncateValues", required = false) final Boolean truncateValues,
      @RequestBody(required = false) final GeneratedUserTaskVariableSearchQueryRequestStrictContract userTaskVariableSearchQueryRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchUserTaskVariables(userTaskKey, truncateValues, userTaskVariableSearchQueryRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/user-tasks/{userTaskKey}/audit-logs/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchUserTaskAuditLogs(
      @PathVariable("userTaskKey") final Long userTaskKey,
      @RequestBody(required = false) final GeneratedUserTaskAuditLogSearchQueryRequestStrictContract userTaskAuditLogSearchQueryRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchUserTaskAuditLogs(userTaskKey, userTaskAuditLogSearchQueryRequest, authentication);
  }
}
