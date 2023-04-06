/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.TokenDto;
import org.camunda.optimize.rest.cloud.CloudSaasMetaInfoService;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Optional;

@AllArgsConstructor
@Path("/token")
@Component
@Slf4j
public class TokenRestService {

  private final Optional<CloudSaasMetaInfoService> cloudSaasMetaInfoService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public TokenDto getCurrentToken(@Context final ContainerRequestContext requestContext) {
    return cloudSaasMetaInfoService
      .map(saasMetaInfoService -> new TokenDto(saasMetaInfoService.getCurrentUserServiceToken().orElse(null)))
      .orElseGet(() -> new TokenDto(null));
  }

}
