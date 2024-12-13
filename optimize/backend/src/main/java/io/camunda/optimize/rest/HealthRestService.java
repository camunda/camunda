/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.HealthRestService.READYZ_PATH;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.service.status.StatusCheckingService;
import jakarta.ws.rs.core.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(REST_API_PATH + READYZ_PATH)
public class HealthRestService {

  public static final String READYZ_PATH = "/readyz";

  private final StatusCheckingService statusCheckingService;

  public HealthRestService(final StatusCheckingService statusCheckingService) {
    this.statusCheckingService = statusCheckingService;
  }

  @GetMapping
  public Response getConnectionStatus() {
    if (statusCheckingService.isConnectedToDatabase()) {
      return Response.ok().build();
    }
    return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
  }
}
