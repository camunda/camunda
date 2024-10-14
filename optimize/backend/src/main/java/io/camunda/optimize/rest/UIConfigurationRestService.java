/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import io.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import io.camunda.optimize.service.UIConfigurationService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.springframework.stereotype.Component;

@Path(UIConfigurationRestService.UI_CONFIGURATION_PATH)
@Component
public class UIConfigurationRestService {

  public static final String UI_CONFIGURATION_PATH = "/ui-configuration";

  private final UIConfigurationService uiConfigurationService;

  public UIConfigurationRestService(final UIConfigurationService uiConfigurationService) {
    this.uiConfigurationService = uiConfigurationService;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public UIConfigurationResponseDto getUIConfiguration() {
    return uiConfigurationService.getUIConfiguration();
  }
}
