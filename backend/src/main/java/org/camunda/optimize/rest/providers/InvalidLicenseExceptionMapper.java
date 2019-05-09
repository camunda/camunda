/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.providers;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
@Slf4j
public class InvalidLicenseExceptionMapper implements ExceptionMapper<InvalidLicenseException> {

  @Override
  public Response toResponse(InvalidLicenseException e) {
    log.debug(e.getMessage(), e);
    ErrorResponseDto response = new ErrorResponseDto();
    response.setErrorMessage(e.getMessage());
    return Response
      .status(Response.Status.BAD_REQUEST)
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(response)
      .build();
  }

}
