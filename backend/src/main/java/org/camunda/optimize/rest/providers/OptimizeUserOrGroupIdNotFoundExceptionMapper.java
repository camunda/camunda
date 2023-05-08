/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.exceptions.OptimizeUserOrGroupIdNotFoundException;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
@Slf4j
public class OptimizeUserOrGroupIdNotFoundExceptionMapper implements ExceptionMapper<OptimizeUserOrGroupIdNotFoundException> {
  private final LocalizationService localizationService;

  public OptimizeUserOrGroupIdNotFoundExceptionMapper(@Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @Override
  public Response toResponse(final OptimizeUserOrGroupIdNotFoundException idNotFoundException) {
    log.info("Mapping OptimizeIdNotFoundException");

    return Response
      .status(Response.Status.BAD_REQUEST)
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(getErrorResponseDto(idNotFoundException))
      .build();
  }

  private ErrorResponseDto getErrorResponseDto(OptimizeUserOrGroupIdNotFoundException exception) {
    String errorCode = exception.getErrorCode();
    String errorMessage = localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    String detailedErrorMessage = exception.getMessage();

    return new ErrorResponseDto(errorCode, errorMessage, detailedErrorMessage);
  }
}
