/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.rest.OnboardingStateRestDto;
import org.camunda.optimize.service.OnboardingService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@AllArgsConstructor
@Path("/onboarding")
@Component
public class OnboardingRestService {
  private final SessionService sessionService;
  private final OnboardingService onboardingService;

  @GET
  @Path("/{key}")
  @Produces(MediaType.APPLICATION_JSON)
  public OnboardingStateRestDto getStateByKey(@Context final ContainerRequestContext requestContext,
                                              @PathParam("key") final String key) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return onboardingService.getStateByKeyAndUser(key, userId)
      .map(onboardingStateDto -> new OnboardingStateRestDto(onboardingStateDto.isSeen()))
      .orElseGet(() -> new OnboardingStateRestDto(false));
  }

  @PUT
  @Path("/{key}")
  @Produces(MediaType.APPLICATION_JSON)
  public void setStateByKey(@Context final ContainerRequestContext requestContext,
                            @PathParam("key") final String key,
                            @NotNull final OnboardingStateRestDto onboardingStateRestDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    onboardingService.setStateByKeyAndUser(key, userId, onboardingStateRestDto.isSeen());
  }
}
