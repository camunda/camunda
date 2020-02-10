/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.IdentityService;
import org.springframework.stereotype.Component;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.Optional;

@AllArgsConstructor
@Secured
@Path("/identity")
@Component
@Slf4j
public class IdentityRestService {

  private final IdentityService identityService;

  @GET
  @Path("/search")
  @Produces(MediaType.APPLICATION_JSON)
  public IdentitySearchResultDto searchIdentity(@QueryParam("terms") final String searchTerms,
                                                @QueryParam("limit") @DefaultValue("25") final int limit) {
    return identityService.searchForIdentities(Optional.ofNullable(searchTerms).orElse(""), limit);
  }

  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public IdentityWithMetadataDto getIdentityById(@PathParam("id") final String identityId) {
    return identityService.getIdentityWithMetadataForId(identityId)
      .orElseThrow(() -> new NotFoundException(
        "Could find neither a user nor a group with the id [" + identityId + "]."
      ));
  }

}
