/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.UserResponseDto;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Optional;

@AllArgsConstructor
@Path(IdentityRestService.IDENTITY_RESOURCE_PATH)
@Component
@Slf4j
public class IdentityRestService {

  public static final String IDENTITY_RESOURCE_PATH = "/identity";
  public static final String IDENTITY_SEARCH_SUB_PATH = "/search";
  public static final String CURRENT_USER_IDENTITY_SUB_PATH = "/current/user";

  private final AbstractIdentityService identityService;
  private final SessionService sessionService;

  @GET
  @Path(IDENTITY_SEARCH_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public IdentitySearchResultResponseDto searchIdentity(@QueryParam("terms") final String searchTerms,
                                                        @QueryParam("limit") @DefaultValue("25") final int limit,
                                                        @QueryParam("excludeUserGroups") final boolean excludeUserGroups,
                                                        @Context ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return identityService.searchForIdentitiesAsUser(
      userId, Optional.ofNullable(searchTerms).orElse(""), limit, excludeUserGroups);
  }

  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public IdentityWithMetadataResponseDto getIdentityById(@PathParam("id") final String identityId,
                                                         @Context ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return identityService.getIdentityWithMetadataForIdAsUser(userId, identityId)
      .orElseThrow(() -> new NotFoundException(
        "Could find neither a user nor a group with the id [" + identityId + "]."
      ));
  }

  @GET
  @Path(CURRENT_USER_IDENTITY_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public UserResponseDto getCurrentUser(@Context final ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final UserDto currentUserDto = identityService.getUserById(userId, requestContext).orElseGet(() -> new UserDto(userId));
    return new UserResponseDto(currentUserDto, identityService.getUserAuthorizations(userId));
  }

}
