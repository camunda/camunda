/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import org.camunda.optimize.service.UIConfigurationService;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Path(UIConfigurationRestService.UI_CONFIGURATION_PATH)
@Component
public class UIConfigurationRestService {

  public static final String UI_CONFIGURATION_PATH = "/ui-configuration";

  private final UIConfigurationService uiConfigurationService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public UIConfigurationResponseDto getUIConfiguration() {
    return uiConfigurationService.getUIConfiguration();
  }
}
