/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import io.camunda.optimize.dto.optimize.rest.AlertEmailValidationResponseDto;
import io.camunda.optimize.service.exceptions.OptimizeAlertEmailValidationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;

@Provider
public class OptimizeAlertEmailValidationExceptionMapper
    implements ExceptionMapper<OptimizeAlertEmailValidationException> {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(OptimizeAlertEmailValidationExceptionMapper.class);

  @Override
  public Response toResponse(
      final OptimizeAlertEmailValidationException optimizeAlertEmailValidationException) {
    log.info("Mapping OptimizeAlertEmailValidationException");

    return Response.status(Response.Status.BAD_REQUEST)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(new AlertEmailValidationResponseDto(optimizeAlertEmailValidationException))
        .build();
  }
}
