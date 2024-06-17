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
import io.camunda.optimize.service.exceptions.OptimizeImportDescriptionNotValidException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
public class OptimizeImportDescriptionNotValidExceptionMapper
    implements ExceptionMapper<OptimizeImportDescriptionNotValidException> {
  public static final String ERROR_CODE = "importDescriptionInvalid";

  private final LocalizationService localizationService;

  public OptimizeImportDescriptionNotValidExceptionMapper(
      @Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @Override
  public Response toResponse(final OptimizeImportDescriptionNotValidException exception) {
    log.info("Mapping OptimizeImportDescriptionNotValidException");

    return Response.status(Response.Status.BAD_REQUEST)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(getDescriptionNotValidResponseDto(exception))
        .build();
  }

  private ErrorResponseDto getDescriptionNotValidResponseDto(
      OptimizeImportDescriptionNotValidException exception) {
    String errorCode = exception.getErrorCode();
    String errorMessage = localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    String detailedErrorMessage = exception.getMessage();
    return new ErrorResponseDto(errorCode, errorMessage, detailedErrorMessage);
  }
}
