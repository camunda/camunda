/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import static org.camunda.optimize.rest.HealthRestService.READYZ_PATH;

@AllArgsConstructor
@Component
@Path(READYZ_PATH)
public class HealthRestService {

  public static final String READYZ_PATH = "/readyz";

  private final StatusCheckingService statusCheckingService;

  @GET
  public Response getImportStatus() {
    if (statusCheckingService.isConnectedToElasticSearch()
      && statusCheckingService.isConnectedToAtLeastOnePlatformEngineOrCloud()) {
      return Response.ok().build();
    }
    return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
  }

}
