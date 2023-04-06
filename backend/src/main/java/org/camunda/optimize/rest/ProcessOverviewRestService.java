/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.processoverview.InitialProcessOwnerDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
import org.camunda.optimize.dto.optimize.rest.sorting.ProcessOverviewSorter;
import org.camunda.optimize.service.ProcessOverviewService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;

@AllArgsConstructor
@Path("/process")
@Component
public class ProcessOverviewRestService {

  private final ProcessOverviewService processOverviewService;
  private final SessionService sessionService;

  @GET
  @Path("/overview")
  @Produces(MediaType.APPLICATION_JSON)
  public List<ProcessOverviewResponseDto> getProcessOverviews(@Context ContainerRequestContext requestContext,
                                                              @BeanParam final ProcessOverviewSorter processOverviewSorter) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    List<ProcessOverviewResponseDto> processOverviewResponseDtos =
      processOverviewService.getAllProcessOverviews(userId, requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE));
    return processOverviewSorter.applySort(processOverviewResponseDtos);
  }

  @PUT
  @Path("/{processDefinitionKey}")
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateProcess(@Context final ContainerRequestContext requestContext,
                            @PathParam("processDefinitionKey") final String processDefKey,
                            @NotNull @Valid @RequestBody ProcessUpdateDto processUpdateDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    processOverviewService.updateProcess(userId, processDefKey, processUpdateDto);
  }

  @POST
  @Path("/initial-owner")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setInitialProcessOwner(@Context final ContainerRequestContext requestContext,
                                     @NotNull @Valid @RequestBody InitialProcessOwnerDto ownerDto) {
    String userId;
    try {
      userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    } catch (NotAuthorizedException e) {
      // If we are using a CloudSaaS Token
      userId = Optional.ofNullable(requestContext.getSecurityContext().getUserPrincipal().getName()).orElse("");
    }
    if (userId.isEmpty()) {
      throw new NotAuthorizedException("Could not resolve user for this request");
    }
    processOverviewService.updateProcessOwnerIfNotSet(userId, ownerDto.getProcessDefinitionKey(), ownerDto.getOwner());
  }

}
