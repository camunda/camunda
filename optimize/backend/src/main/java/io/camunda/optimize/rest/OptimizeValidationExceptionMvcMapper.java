/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import io.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class OptimizeValidationExceptionMvcMapper {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(OptimizeValidationExceptionMvcMapper.class);

  @Autowired private LocalizationService localizationService;

  @ExceptionHandler(OptimizeValidationException.class)
  public ResponseEntity<ErrorResponseDto> handleOptimizeValidationException(
      final OptimizeValidationException validationException) {
    LOG.info("Mapping OptimizeValidationException");

    // Map exception to ErrorResponseDto
    final ErrorResponseDto errorResponseDto = getErrorResponseDto(validationException);

    // Return a 400 BAD_REQUEST response with the ErrorResponseDto as the body
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(errorResponseDto);
  }

  private ErrorResponseDto getErrorResponseDto(final OptimizeValidationException exception) {
    final String errorCode = exception.getErrorCode();
    final String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    final String detailedErrorMessage = exception.getMessage();

    return new ErrorResponseDto(errorCode, errorMessage, detailedErrorMessage);
  }
}
