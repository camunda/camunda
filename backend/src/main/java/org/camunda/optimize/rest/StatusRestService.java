/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.status.StatusResponseDto;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@AllArgsConstructor
@Path(StatusRestService.STATUS_PATH)
@Component
public class StatusRestService {

  public static final String STATUS_PATH = "/status";
  private final StatusCheckingService statusCheckingService;

  /**
   * States if optimize is still importing
   * also includes connection status to Elasticsearch and the Engine
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public StatusResponseDto getImportStatus() {
    return statusCheckingService.getStatusResponse();
  }

}
