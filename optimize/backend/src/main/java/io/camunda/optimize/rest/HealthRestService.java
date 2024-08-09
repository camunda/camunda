/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.HealthRestService.READYZ_PATH;

import io.camunda.optimize.service.status.StatusCheckingService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Path(READYZ_PATH)
public class HealthRestService {

  public static final String READYZ_PATH = "/readyz";

  private final StatusCheckingService statusCheckingService;

  @GET
  public Response getConnectionStatus() {
    if (statusCheckingService.isConnectedToDatabase()
        && statusCheckingService.isConnectedToAtLeastOnePlatformEngineOrCloud()) {
      return Response.ok().build();
    }
    return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
  }
}
