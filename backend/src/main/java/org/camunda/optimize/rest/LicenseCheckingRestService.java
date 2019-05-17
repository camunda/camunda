/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.optimize.dto.optimize.query.LicenseInformationDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.license.LicenseManager;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@AllArgsConstructor
@Path("/license")
@Component
public class LicenseCheckingRestService {

  private final LicenseManager licenseManager;

  @POST
  @Path("/validate-and-store")
  @Consumes(MediaType.TEXT_PLAIN)
  @Produces(MediaType.APPLICATION_JSON)
  public LicenseInformationDto validateOptimizeLicenseAndStoreIt(String license) throws OptimizeException, InvalidLicenseException {
    LicenseInformationDto licenseInformationDto = licenseManager.validateOptimizeLicense(license);
    licenseManager.storeLicense(license);
    return licenseInformationDto;
  }

  @GET
  @Path("/validate")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public LicenseInformationDto validateLicenseStoredInOptimize() throws InvalidLicenseException {
    LicenseInformationDto licenseInformationDto = licenseManager.validateLicenseStoredInOptimize();
    return licenseInformationDto;
  }
}
