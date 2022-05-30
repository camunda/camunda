/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOwnerDto;
import org.camunda.optimize.service.ProcessOverviewService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@AllArgsConstructor
@Path("/process")
@Component
public class ProcessOverviewRestService {

  private final ProcessOverviewService processOverviewService;
  private final SessionService sessionService;

  @GET
  @Path("/overview")
  @Produces(MediaType.APPLICATION_JSON)
  public List<ProcessOverviewResponseDto> getProcessOverviews(@Context ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return processOverviewService.getAllProcessOverviews(userId);
  }

  @PUT
  @Path("/{processDefinitionKey}/digest")
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateProcessesDigest(@Context final ContainerRequestContext requestContext,
                                    @PathParam("processDefinitionKey") final String processDefKey,
                                    @NotNull @Valid @RequestBody ProcessDigestRequestDto processDigest) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    processOverviewService.updateProcessDigest(userId, processDefKey, processDigest);
  }

  @PUT
  @Path("/{processDefinitionKey}/owner-new") // TODO remove "-new" with OPT-6175
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateProcessOwner(@Context final ContainerRequestContext requestContext,
                                 @PathParam("processDefinitionKey") final String processDefKey,
                                 @NotNull @Valid @RequestBody ProcessOwnerDto ownerDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    processOverviewService.updateProcessOwner(userId, processDefKey, ownerDto.getId());
  }
}
