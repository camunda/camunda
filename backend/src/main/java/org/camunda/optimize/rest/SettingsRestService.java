/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.service.SettingsService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@AllArgsConstructor
@Path("/settings")
@Component
public class SettingsRestService {

  private final SessionService sessionService;
  private final SettingsService settingsService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public SettingsResponseDto getSettings(@Context final ContainerRequestContext requestContext) {
    return settingsService.getSettings();
  }

  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  public void setSettings(@Context final ContainerRequestContext requestContext,
                          @NotNull final SettingsResponseDto settingsDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    settingsService.setSettings(userId, settingsDto);
  }

}
