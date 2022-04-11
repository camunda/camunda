/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@AllArgsConstructor
@Path("/alert")
@Component
public class AlertRestService {

  private final AlertService alertService;
  private final SessionService sessionService;

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdResponseDto createAlert(@Context ContainerRequestContext requestContext,
                                   AlertCreationRequestDto toCreate) {
    ValidationHelper.ensureNotNull("creation object", toCreate);
    String user = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return alertService.createAlert(toCreate, user);
  }

  @PUT
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateAlert(@Context ContainerRequestContext requestContext,
                          @PathParam("id") String alertId,
                          AlertCreationRequestDto toCreate) {
    ValidationHelper.ensureNotNull("creation object", toCreate);
    String user = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    alertService.updateAlert(alertId, toCreate, user);
  }

  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteAlert(@Context ContainerRequestContext requestContext,
                          @PathParam("id") String alertId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    alertService.deleteAlert(alertId, userId);
  }

  @POST
  @Path("/delete")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteAlerts(@Context ContainerRequestContext requestContext,
                           @NotNull @RequestBody List<String> alertIds) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    alertService.deleteAlerts(alertIds, userId);
  }
}
