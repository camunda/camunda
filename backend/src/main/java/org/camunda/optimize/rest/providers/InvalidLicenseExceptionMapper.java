/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.providers;

import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class InvalidLicenseExceptionMapper implements ExceptionMapper<InvalidLicenseException> {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  public Response toResponse(InvalidLicenseException e) {
    logger.debug(e.getMessage(), e);
    ErrorResponseDto response = new ErrorResponseDto();
    response.setErrorMessage(e.getMessage());
    return Response
      .status(Response.Status.BAD_REQUEST)
      .entity(response)
      .build();
  }
}
