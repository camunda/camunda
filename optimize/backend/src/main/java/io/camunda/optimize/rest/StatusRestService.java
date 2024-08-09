/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import io.camunda.optimize.dto.optimize.query.status.StatusResponseDto;
import io.camunda.optimize.service.status.StatusCheckingService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Path(StatusRestService.STATUS_PATH)
@Component
public class StatusRestService {

  public static final String STATUS_PATH = "/status";
  private final StatusCheckingService statusCheckingService;

  /**
   * States if optimize is still importing also includes connection status to Elasticsearch and the
   * Engine
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public StatusResponseDto getImportStatus() {
    return statusCheckingService.getStatusResponse();
  }
}
