/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.ElementInstanceServices;
import io.camunda.service.ElementInstanceServices.SetVariablesRequest;
import io.camunda.zeebe.gateway.protocol.rest.SetVariableRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/element-instances")
public class ElementInstanceController {

  private final ElementInstanceServices elementInstanceServices;

  public ElementInstanceController(final ElementInstanceServices elementInstanceServices) {
    this.elementInstanceServices = elementInstanceServices;
  }

  @PutMapping(
      path = "/{elementInstanceKey}/variables",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> setVariables(
      @PathVariable final long elementInstanceKey,
      @RequestBody final SetVariableRequest variableRequest) {
    return RequestMapper.toVariableRequest(variableRequest, elementInstanceKey)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::setVariables);
  }

  private CompletableFuture<ResponseEntity<Object>> setVariables(
      final SetVariablesRequest variablesRequest) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            elementInstanceServices
                .withAuthentication(RequestMapper.getAuthentication())
                .setVariables(variablesRequest));
  }
}
