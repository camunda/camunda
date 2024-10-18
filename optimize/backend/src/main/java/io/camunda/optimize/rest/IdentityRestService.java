/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import io.camunda.optimize.dto.optimize.rest.UserResponseDto;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Path(IdentityRestService.IDENTITY_RESOURCE_PATH)
@Component
public class IdentityRestService {

  public static final String IDENTITY_RESOURCE_PATH = "/identity";
  public static final String IDENTITY_SEARCH_SUB_PATH = "/search";
  public static final String CURRENT_USER_IDENTITY_SUB_PATH = "/current/user";
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(IdentityRestService.class);

  private final AbstractIdentityService identityService;
  private final SessionService sessionService;

  public IdentityRestService(
      final AbstractIdentityService identityService, final SessionService sessionService) {
    this.identityService = identityService;
    this.sessionService = sessionService;
  }

  @GET
  @Path(IDENTITY_SEARCH_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public IdentitySearchResultResponseDto searchIdentity(
      @QueryParam("terms") final String searchTerms,
      @QueryParam("limit") @DefaultValue("25") final int limit,
      @QueryParam("excludeUserGroups") final boolean excludeUserGroups,
      @Context final ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return identityService.searchForIdentitiesAsUser(
        userId, Optional.ofNullable(searchTerms).orElse(""), limit, excludeUserGroups);
  }

  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public IdentityWithMetadataResponseDto getIdentityById(
      @PathParam("id") final String identityId,
      @Context final ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return identityService
        .getIdentityWithMetadataForIdAsUser(userId, identityId)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Could find neither a user nor a group with the id [" + identityId + "]."));
  }

  @GET
  @Path(CURRENT_USER_IDENTITY_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public UserResponseDto getCurrentUser(@Context final ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final UserDto currentUserDto =
        identityService
            .getCurrentUserById(userId, requestContext)
            .orElseGet(() -> new UserDto(userId));
    return new UserResponseDto(currentUserDto, identityService.getEnabledAuthorizations());
  }
}
