/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.service.LocalizationService;

@Provider
@Slf4j
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

  private final LocalizationService localizationService;
  private static final String FORBIDDEN_ERROR_CODE = "forbiddenError";

  public ForbiddenExceptionMapper(@Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @Override
  public Response toResponse(ForbiddenException forbiddenException) {
    log.info("Mapping ForbiddenException");

    return Response.status(Response.Status.FORBIDDEN)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(getErrorResponseDto(forbiddenException))
        .build();
  }

  private ErrorResponseDto getErrorResponseDto(ForbiddenException exception) {
    String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(FORBIDDEN_ERROR_CODE);
    String detailedErrorMessage = exception.getMessage();

    return new ErrorResponseDto(FORBIDDEN_ERROR_CODE, errorMessage, detailedErrorMessage);
  }
}
