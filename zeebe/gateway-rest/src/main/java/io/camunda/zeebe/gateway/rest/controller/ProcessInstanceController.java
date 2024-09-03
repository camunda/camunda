/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceStartRequest;
import io.camunda.zeebe.gateway.protocol.rest.StartProcessInstanceRequest;
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
  public CompletableFuture<ResponseEntity<Object>> startProcessInstance(
      @RequestBody final StartProcessInstanceRequest request) {
    return RequestMapper.toStartProcessInstance(request)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::startProcessInstance);
  }

  private CompletableFuture<ResponseEntity<Object>> startProcessInstance(
      final ProcessInstanceStartRequest request) {
    if (request.awaitCompletion()) {
      return RequestMapper.executeServiceMethod(
          () ->
              processInstanceServices
                  .withAuthentication(RequestMapper.getAuthentication())
                  .startProcessInstanceWithResult(request),
          ResponseMapper::toStartProcessInstanceWithResultResponse);
    }
    return RequestMapper.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(RequestMapper.getAuthentication())
                .startProcessInstance(request),
        ResponseMapper::toStartProcessInstanceResponse);
  }
}
