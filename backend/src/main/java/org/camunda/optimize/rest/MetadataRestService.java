/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.OptimizeVersionDto;
import org.camunda.optimize.service.metadata.OptimizeVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/meta")
@Component
public class MetadataRestService {

  @Autowired
  private OptimizeVersionService versionService;

  /**
   * Returns the current Optimize version.
   */
  @GET
  @Path("/version")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.WILDCARD)
  public OptimizeVersionDto getOptimizeVersion() {
    return new OptimizeVersionDto(versionService.getRawVersion());
  }

}
