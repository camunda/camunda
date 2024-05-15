/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.SettingsDto;
import org.camunda.optimize.service.SettingsService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Path("/settings")
@Component
public class SettingsRestService {

  private final SessionService sessionService;
  private final SettingsService settingsService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public SettingsDto getSettings() {
    return settingsService.getSettings();
  }

  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  public void setSettings(
      @Context final ContainerRequestContext requestContext,
      @NotNull final SettingsDto settingsDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    settingsService.setSettings(userId, settingsDto);
  }
}
