/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.LicenseInformationResponseDto;
import org.camunda.optimize.service.license.LicenseManager;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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
    LicenseInformationResponseDto licenseInformationDto = licenseManager.validateOptimizeLicense(license);
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
