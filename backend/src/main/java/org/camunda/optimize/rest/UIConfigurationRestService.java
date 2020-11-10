/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import org.camunda.optimize.service.UIConfigurationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@AllArgsConstructor
@Path("/ui-configuration")
@Component
public class UIConfigurationRestService {

  private final UIConfigurationService uiConfigurationService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public UIConfigurationResponseDto getUIConfiguration() {
    return uiConfigurationService.getUIConfiguration();
  }

}
