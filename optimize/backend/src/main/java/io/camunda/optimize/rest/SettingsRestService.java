/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import io.camunda.optimize.dto.optimize.SettingsDto;
import io.camunda.optimize.service.SettingsService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.springframework.stereotype.Component;

@Path("/settings")
@Component
public class SettingsRestService {

  private final SessionService sessionService;
  private final SettingsService settingsService;

  public SettingsRestService(
      final SessionService sessionService, final SettingsService settingsService) {
    this.sessionService = sessionService;
    this.settingsService = settingsService;
  }

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
