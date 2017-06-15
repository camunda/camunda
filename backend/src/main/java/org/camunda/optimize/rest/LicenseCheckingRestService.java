package org.camunda.optimize.rest;

import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.optimize.dto.optimize.query.LicenseInformationDto;
import org.camunda.optimize.service.license.LicenseManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.camunda.optimize.rest.util.RestResponseUtil.buildOkResponse;
import static org.camunda.optimize.rest.util.RestResponseUtil.buildServerErrorResponse;


@Path("/license")
@Component
public class LicenseCheckingRestService {

  private final LicenseManager licenseManager;

  @Autowired
  public LicenseCheckingRestService(LicenseManager licenseManager) {
    this.licenseManager = licenseManager;
  }

  @POST
  @Path("/validate-and-store")
  @Consumes(MediaType.TEXT_PLAIN)
  @Produces(MediaType.APPLICATION_JSON)
  public Response validateOptimizeLicenseAndStoreIt(String license) {
    try {
      LicenseInformationDto licenseInformationDto = licenseManager.validateOptimizeLicense(license);
      licenseManager.storeLicense(license);
      return buildOkResponse(licenseInformationDto);
    } catch (Exception e) {
      return buildServerErrorResponse(e);
    }
  }

  @GET
  @Path("/validate")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public Response validateLicenseStoredOptimizeLicense() {
    try {
      String license = licenseManager.retrieveStoredOptimizeLicense();
      LicenseInformationDto licenseInformationDto = licenseManager.validateOptimizeLicense(license);
      return buildOkResponse(licenseInformationDto);
    } catch (InvalidLicenseException e) {
      return buildServerErrorResponse(e);
    }
  }
}
