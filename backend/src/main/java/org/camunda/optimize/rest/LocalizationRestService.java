/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.rest.providers.CacheRequest;
import org.camunda.optimize.service.LocalizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

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

  @GET
  @Produces("text/markdown")
  @Path("/whatsnew")
  @CacheRequest
  public byte[] getWhatsNewMarkdown(@QueryParam("localeCode") final String localeCode) {
    return localizationService.getLocalizedWhatsNewMarkdown(localeCode);
  }
}
