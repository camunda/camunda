/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import io.camunda.optimize.service.alert.AlertService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.util.ValidationHelper;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

@Path("/alert")
@Component
public class AlertRestService {

  private final AlertService alertService;
  private final SessionService sessionService;

  public AlertRestService(final AlertService alertService, final SessionService sessionService) {
    this.alertService = alertService;
    this.sessionService = sessionService;
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdResponseDto createAlert(
      @Context final ContainerRequestContext requestContext,
      final AlertCreationRequestDto toCreate) {
    ValidationHelper.ensureNotNull("creation object", toCreate);
    final String user = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return alertService.createAlert(toCreate, user);
  }

  @PUT
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateAlert(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String alertId,
      final AlertCreationRequestDto toCreate) {
    ValidationHelper.ensureNotNull("creation object", toCreate);
    final String user = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    alertService.updateAlert(alertId, toCreate, user);
  }

  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteAlert(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String alertId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    alertService.deleteAlert(alertId, userId);
  }

  @POST
  @Path("/delete")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteAlerts(
      @Context final ContainerRequestContext requestContext,
      @NotNull @RequestBody final List<String> alertIds) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    alertService.deleteAlerts(alertIds, userId);
  }
}
