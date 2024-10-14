/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;

import io.camunda.optimize.dto.optimize.query.processoverview.InitialProcessOwnerDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
import io.camunda.optimize.dto.optimize.rest.sorting.ProcessOverviewSorter;
import io.camunda.optimize.service.ProcessOverviewService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

@Path("/process")
@Component
public class ProcessOverviewRestService {

  private final ProcessOverviewService processOverviewService;
  private final SessionService sessionService;

  public ProcessOverviewRestService(
      final ProcessOverviewService processOverviewService, final SessionService sessionService) {
    this.processOverviewService = processOverviewService;
    this.sessionService = sessionService;
  }

  @GET
  @Path("/overview")
  @Produces(MediaType.APPLICATION_JSON)
  public List<ProcessOverviewResponseDto> getProcessOverviews(
      @Context final ContainerRequestContext requestContext,
      @BeanParam final ProcessOverviewSorter processOverviewSorter) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final List<ProcessOverviewResponseDto> processOverviewResponseDtos =
        processOverviewService.getAllProcessOverviews(
            userId, requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE));
    return processOverviewSorter.applySort(processOverviewResponseDtos);
  }

  @PUT
  @Path("/{processDefinitionKey}")
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateProcess(
      @Context final ContainerRequestContext requestContext,
      @PathParam("processDefinitionKey") final String processDefKey,
      @NotNull @Valid @RequestBody final ProcessUpdateDto processUpdateDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    processOverviewService.updateProcess(userId, processDefKey, processUpdateDto);
  }

  @POST
  @Path("/initial-owner")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setInitialProcessOwner(
      @Context final ContainerRequestContext requestContext,
      @NotNull @Valid @RequestBody final InitialProcessOwnerDto ownerDto) {
    Optional<String> userId;
    try {
      userId =
          Optional.ofNullable(sessionService.getRequestUserOrFailNotAuthorized(requestContext));
    } catch (final NotAuthorizedException e) {
      // If we are using a CloudSaaS Token
      userId =
          Optional.ofNullable(requestContext.getSecurityContext().getUserPrincipal().getName());
    }
    userId.ifPresentOrElse(
        id ->
            processOverviewService.updateProcessOwnerIfNotSet(
                id, ownerDto.getProcessDefinitionKey(), ownerDto.getOwner()),
        () -> {
          throw new NotAuthorizedException("Could not resolve user for this request");
        });
  }
}
