/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.IncidentServices;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("v2/incidents")
public class IncidentController {

  private final IncidentServices incidentServices;

  public IncidentController(final IncidentServices incidentServices) {
    this.incidentServices = incidentServices;
  }

  @PostMapping(
      path = "/{incidentKey}/resolution",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> incidentResolution(
      @PathVariable final long incidentKey) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            incidentServices
                .withAuthentication(RequestMapper.getAuthentication())
                .resolveIncident(incidentKey));
  }
}
