/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.exceptions.OptimizeImportDescriptionNotValidException;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
@Slf4j
public class OptimizeImportDescriptionNotValidExceptionMapper
  implements ExceptionMapper<OptimizeImportDescriptionNotValidException> {
  public static final String ERROR_CODE = "importDescriptionInvalid";

  private final LocalizationService localizationService;

  public OptimizeImportDescriptionNotValidExceptionMapper(@Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @Override
  public Response toResponse(final OptimizeImportDescriptionNotValidException exception) {
    log.info("Mapping OptimizeImportDescriptionNotValidException");

    return Response
      .status(Response.Status.BAD_REQUEST)
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(getDescriptionNotValidResponseDto(exception))
      .build();
  }

  private ErrorResponseDto getDescriptionNotValidResponseDto(OptimizeImportDescriptionNotValidException exception) {
    String errorCode = exception.getErrorCode();
    String errorMessage = localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    String detailedErrorMessage = exception.getMessage();
    return new ErrorResponseDto(errorCode, errorMessage, detailedErrorMessage);
  }
}
