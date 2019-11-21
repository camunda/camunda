/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.providers;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.service.LocalizationService;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
@Slf4j
public class InvalidLicenseExceptionMapper implements ExceptionMapper<InvalidLicenseException> {

  private final LocalizationService localizationService;
  private static final String INVALID_LICENSE_ERROR_CODE = "invalidLicenseError";

  public InvalidLicenseExceptionMapper(@Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @Override
  public Response toResponse(InvalidLicenseException e) {
    log.debug("Mapping InvalidLicenseException.");
    return Response
      .status(Response.Status.BAD_REQUEST)
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(getErrorResponseDto(e))
      .build();
  }

  private ErrorResponseDto getErrorResponseDto(InvalidLicenseException exception) {
    String errorMessage = localizationService.getDefaultLocaleMessageForApiErrorCode(INVALID_LICENSE_ERROR_CODE);
    String detailedErrorMessage = exception.getMessage();

    return new ErrorResponseDto(INVALID_LICENSE_ERROR_CODE, errorMessage, detailedErrorMessage);
  }

}
