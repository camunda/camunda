/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import io.camunda.optimize.rest.providers.CacheRequest;
import io.camunda.optimize.service.LocalizationService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Path(LocalizationRestService.LOCALIZATION_PATH)
@Component
@Slf4j
public class LocalizationRestService {

  public static final String LOCALIZATION_PATH = "/localization";
  private final LocalizationService localizationService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @CacheRequest
  public byte[] getLocalizationFile(@QueryParam("localeCode") final String localeCode) {
    return localizationService.getLocalizationFileBytes(localeCode);
  }
}
