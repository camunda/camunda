/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.query.TokenDto;
import io.camunda.optimize.rest.cloud.CloudSaasMetaInfoService;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(REST_API_PATH + TokenRestService.TOKEN_PATH)
public class TokenRestService {

  public static final String TOKEN_PATH = "/token";

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(TokenRestService.class);
  private final Optional<CloudSaasMetaInfoService> cloudSaasMetaInfoService;

  public TokenRestService(final Optional<CloudSaasMetaInfoService> cloudSaasMetaInfoService) {
    this.cloudSaasMetaInfoService = cloudSaasMetaInfoService;
  }

  @GetMapping
  public TokenDto getCurrentToken() {
    return cloudSaasMetaInfoService
        .map(
            saasMetaInfoService ->
                new TokenDto(saasMetaInfoService.getCurrentUserServiceToken().orElse(null)))
        .orElseGet(() -> new TokenDto(null));
  }
}
