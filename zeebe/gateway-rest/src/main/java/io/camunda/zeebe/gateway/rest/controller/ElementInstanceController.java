/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.ElementServices;
import io.camunda.service.ElementServices.SetVariablesRequest;
import io.camunda.zeebe.gateway.protocol.rest.VariableRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/element-instances")
public class ElementInstanceController {

  private final ElementServices elementServices;

  @Autowired
  public ElementInstanceController(final ElementServices elementServices) {
    this.elementServices = elementServices;
  }

  @PutMapping(
      path = "/{elementInstanceKey}/variables",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> setVariables(
      @PathVariable final long elementInstanceKey,
      @RequestBody final VariableRequest variableRequest) {
    return RequestMapper.toVariableRequest(variableRequest, elementInstanceKey)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::variables);
  }

  private CompletableFuture<ResponseEntity<Object>> variables(
      final SetVariablesRequest variablesRequest) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            elementServices
                .withAuthentication(RequestMapper.getAuthentication())
                .setVariables(variablesRequest));
  }
}
