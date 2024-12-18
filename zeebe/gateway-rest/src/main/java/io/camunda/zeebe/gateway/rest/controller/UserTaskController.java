/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.UserTaskServices;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RequestMapper.AssignUserTaskRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper.CompleteUserTaskRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper.UpdateUserTaskRequest;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPatchMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping(path = {"/v1/user-tasks", "/v2/user-tasks"})
public class UserTaskController {

  private final UserTaskServices userTaskServices;

  @Autowired
  public UserTaskController(final UserTaskServices userTaskServices) {
    this.userTaskServices = userTaskServices;
  }

  @CamundaPostMapping(path = "/{userTaskKey}/completion")
  public CompletableFuture<ResponseEntity<Object>> completeUserTask(
      @PathVariable final long userTaskKey,
      @RequestBody(required = false) final UserTaskCompletionRequest completionRequest) {

    return completeUserTask(
        RequestMapper.toUserTaskCompletionRequest(completionRequest, userTaskKey));
  }

  @CamundaPostMapping(path = "/{userTaskKey}/assignment")
  public CompletableFuture<ResponseEntity<Object>> assignUserTask(
      @PathVariable final long userTaskKey,
      @RequestBody final UserTaskAssignmentRequest assignmentRequest) {

    return RequestMapper.toUserTaskAssignmentRequest(assignmentRequest, userTaskKey)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::assignUserTask);
  }

  @CamundaDeleteMapping(path = "/{userTaskKey}/assignee")
  public CompletableFuture<ResponseEntity<Object>> unassignUserTask(
      @PathVariable final long userTaskKey) {

    return unassignUserTask(RequestMapper.toUserTaskUnassignmentRequest(userTaskKey));
  }

  @CamundaPatchMapping(path = "/{userTaskKey}")
  public CompletableFuture<ResponseEntity<Object>> updateUserTask(
      @PathVariable final long userTaskKey,
      @RequestBody(required = false) final UserTaskUpdateRequest updateRequest) {

    return RequestMapper.toUserTaskUpdateRequest(updateRequest, userTaskKey)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateUserTask);
  }

  private CompletableFuture<ResponseEntity<Object>> assignUserTask(
      final AssignUserTaskRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices
                .withAuthentication(RequestMapper.getAuthentication())
                .assignUserTask(
                    request.userTaskKey(),
                    request.assignee(),
                    request.action(),
                    request.allowOverride()));
  }

  private CompletableFuture<ResponseEntity<Object>> completeUserTask(
      final CompleteUserTaskRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices
                .withAuthentication(RequestMapper.getAuthentication())
                .completeUserTask(request.userTaskKey(), request.variables(), request.action()));
  }

  private CompletableFuture<ResponseEntity<Object>> unassignUserTask(
      final AssignUserTaskRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices
                .withAuthentication(RequestMapper.getAuthentication())
                .unassignUserTask(request.userTaskKey(), request.action()));
  }

  private CompletableFuture<ResponseEntity<Object>> updateUserTask(
      final UpdateUserTaskRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices
                .withAuthentication(RequestMapper.getAuthentication())
                .updateUserTask(request.userTaskKey(), request.changeset(), request.action()));
  }
}
