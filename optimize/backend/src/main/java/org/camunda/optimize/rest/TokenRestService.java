/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.TokenDto;
import org.camunda.optimize.rest.cloud.CloudSaasMetaInfoService;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Path("/token")
@Component
@Slf4j
public class TokenRestService {

  private final Optional<CloudSaasMetaInfoService> cloudSaasMetaInfoService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public TokenDto getCurrentToken() {
    return cloudSaasMetaInfoService
        .map(
            saasMetaInfoService ->
                new TokenDto(saasMetaInfoService.getCurrentUserServiceToken().orElse(null)))
        .orElseGet(() -> new TokenDto(null));
  }
}
