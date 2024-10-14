/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import io.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import io.camunda.optimize.service.LocalizationService;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;

@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ForbiddenExceptionMapper.class);
  private static final String FORBIDDEN_ERROR_CODE = "forbiddenError";
  private final LocalizationService localizationService;

  public ForbiddenExceptionMapper(@Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @Override
  public Response toResponse(final ForbiddenException forbiddenException) {
    log.info("Mapping ForbiddenException");

    return Response.status(Response.Status.FORBIDDEN)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(getErrorResponseDto(forbiddenException))
        .build();
  }

  private ErrorResponseDto getErrorResponseDto(final ForbiddenException exception) {
    final String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(FORBIDDEN_ERROR_CODE);
    final String detailedErrorMessage = exception.getMessage();

    return new ErrorResponseDto(FORBIDDEN_ERROR_CODE, errorMessage, detailedErrorMessage);
  }
}
