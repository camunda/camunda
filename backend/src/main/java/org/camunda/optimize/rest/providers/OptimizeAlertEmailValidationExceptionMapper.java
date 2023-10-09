/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.AlertEmailValidationResponseDto;
import org.camunda.optimize.service.exceptions.OptimizeAlertEmailValidationException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
@Slf4j
public class OptimizeAlertEmailValidationExceptionMapper implements ExceptionMapper<OptimizeAlertEmailValidationException> {

  @Override
  public Response toResponse(final OptimizeAlertEmailValidationException optimizeAlertEmailValidationException) {
    log.info("Mapping OptimizeAlertEmailValidationException");

    return Response
      .status(Response.Status.BAD_REQUEST)
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(new AlertEmailValidationResponseDto(optimizeAlertEmailValidationException))
      .build();
  }

}
