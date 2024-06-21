/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import io.camunda.optimize.dto.optimize.query.LicenseInformationResponseDto;
import io.camunda.optimize.service.license.LicenseManager;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
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
  public LicenseInformationResponseDto validateOptimizeLicenseAndStoreIt(final String license) {
    final LicenseInformationResponseDto licenseInformationDto =
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
