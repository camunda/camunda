/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyRequest;
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
import io.camunda.zeebe.gateway.protocol.rest.CancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.rest.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.rest.MigrateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.rest.ModifyProcessInstanceRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/process-instances")
public class ProcessInstanceController {

  private final ProcessInstanceServices processInstanceServices;
  private final MultiTenancyCfg multiTenancyCfg;

  public ProcessInstanceController(
      final ProcessInstanceServices processInstanceServices,
      final MultiTenancyCfg multiTenancyCfg) {
    this.processInstanceServices = processInstanceServices;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @PostMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> createProcessInstance(
      @RequestBody final CreateProcessInstanceRequest request) {
    return RequestMapper.toCreateProcessInstance(request, multiTenancyCfg.isEnabled())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createProcessInstance);
  }

  @PostMapping(
      path = "/{processInstanceKey}/cancellation",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> cancelProcessInstance(
      @PathVariable final long processInstanceKey,
      @RequestBody(required = false) final CancelProcessInstanceRequest cancelRequest) {
    return RequestMapper.toCancelProcessInstance(processInstanceKey, cancelRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::cancelProcessInstance);
  }

  @PostMapping(
      path = "/{processInstanceKey}/migration",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> migrateProcessInstance(
      @PathVariable final long processInstanceKey,
      @RequestBody final MigrateProcessInstanceRequest migrationRequest) {
    return RequestMapper.toMigrateProcessInstance(processInstanceKey, migrationRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::migrateProcessInstance);
  }

  @PostMapping(
      path = "/{processInstanceKey}/modification",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> modifyProcessInstance(
      @PathVariable final long processInstanceKey,
      @RequestBody final ModifyProcessInstanceRequest modifyRequest) {
    return RequestMapper.toModifyProcessInstance(processInstanceKey, modifyRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::modifyProcessInstance);
  }

  private CompletableFuture<ResponseEntity<Object>> createProcessInstance(
      final ProcessInstanceCreateRequest request) {
    if (request.awaitCompletion()) {
      return RequestMapper.executeServiceMethod(
          () ->
              processInstanceServices
                  .withAuthentication(RequestMapper.getAuthentication())
                  .createProcessInstanceWithResult(request),
          ResponseMapper::toCreateProcessInstanceWithResultResponse);
    }
    return RequestMapper.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createProcessInstance(request),
        ResponseMapper::toCreateProcessInstanceResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> cancelProcessInstance(
      final ProcessInstanceCancelRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices
                .withAuthentication(RequestMapper.getAuthentication())
                .cancelProcessInstance(request));
  }

  private CompletableFuture<ResponseEntity<Object>> migrateProcessInstance(
      final ProcessInstanceMigrateRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices
                .withAuthentication(RequestMapper.getAuthentication())
                .migrateProcessInstance(request));
  }

  private CompletableFuture<ResponseEntity<Object>> modifyProcessInstance(
      final ProcessInstanceModifyRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices
                .withAuthentication(RequestMapper.getAuthentication())
                .modifyProcessInstance(request));
  }
}
