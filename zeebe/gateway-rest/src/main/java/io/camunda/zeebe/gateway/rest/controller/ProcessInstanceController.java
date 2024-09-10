/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/process-instances")
public class ProcessInstanceController {

  @Autowired private ProcessInstanceServices processInstanceServices;

  @PostMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> createProcessInstance(
      @RequestBody final CreateProcessInstanceRequest request) {
    return RequestMapper.toCreateProcessInstance(request)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createProcessInstance);
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
}
