/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.ProcessTestServices;
import io.camunda.service.processtest.TestSpecificationResult;
import io.camunda.service.processtest.dsl.TestSpecification;
import io.camunda.zeebe.gateway.protocol.rest.ProcessTestExecuteRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/process-tests")
public class ProcessTestController {

  private final ProcessTestServices processTestServices;

  public ProcessTestController(final ProcessTestServices processTestServices) {
    this.processTestServices = processTestServices;
  }

  @PostMapping(
      path = "/execute",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> executeTests(
      @RequestBody final ProcessTestExecuteRequest request) {

    return RequestMapper.toProcessTestSpecification(request)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::execute);
  }

  private CompletableFuture<ResponseEntity<Object>> execute(
      final TestSpecification testSpecification) {

    final TestSpecificationResult result = processTestServices.execute(testSpecification);

    final CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
    future.complete(ResponseMapper.toProcessTestExecuteResponse(result));

    return future;
  }
}
