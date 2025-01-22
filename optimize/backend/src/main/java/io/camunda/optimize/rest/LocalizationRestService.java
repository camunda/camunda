/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.rest.providers.CacheRequest;
import io.camunda.optimize.service.LocalizationService;
import org.slf4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(REST_API_PATH + LocalizationRestService.LOCALIZATION_PATH)
public class LocalizationRestService {

  public static final String LOCALIZATION_PATH = "/localization";
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(LocalizationRestService.class);
  private final LocalizationService localizationService;

  public LocalizationRestService(final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @CacheRequest
  public byte[] getLocalizationFile(
      @RequestParam(name = "localeCode", required = false) final String localeCode) {
    return localizationService.getLocalizationFileBytes(localeCode);
  }
}
