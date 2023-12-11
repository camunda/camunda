/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.LicenseInformationResponseDto;
import org.camunda.optimize.service.license.LicenseManager;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Path(LicenseCheckingRestService.LICENSE_PATH)
@Component
public class LicenseCheckingRestService {

  public static final String LICENSE_PATH = "/license";
  private final LicenseManager licenseManager;

  @POST
  @Path("/validate-and-store")
  @Consumes(MediaType.TEXT_PLAIN)
  @Produces(MediaType.APPLICATION_JSON)
  public LicenseInformationResponseDto validateOptimizeLicenseAndStoreIt(String license) {
    LicenseInformationResponseDto licenseInformationDto =
      licenseManager.validateOptimizeLicense(license);
    licenseManager.storeLicense(license);
    return licenseInformationDto;
  }

  @GET
  @Path("/validate")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public LicenseInformationResponseDto validateLicenseStoredInOptimize() {
    return licenseManager.validateLicenseStoredInOptimize();
  }
}
