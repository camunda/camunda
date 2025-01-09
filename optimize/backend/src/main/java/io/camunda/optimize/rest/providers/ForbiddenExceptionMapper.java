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
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE) // This mapper takes precedence over GenericExceptionMapper
public class ForbiddenExceptionMapper {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ForbiddenExceptionMapper.class);
  private static final String FORBIDDEN_ERROR_CODE = "forbiddenError";
  @Autowired private LocalizationService localizationService;

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorResponseDto> handleForbiddenException(
      final ForbiddenException forbiddenException) {
    LOG.info("Mapping ForbiddenException");
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(getErrorResponseDto(forbiddenException));
  }

  private ErrorResponseDto getErrorResponseDto(final ForbiddenException exception) {
    final String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(FORBIDDEN_ERROR_CODE);
    final String detailedErrorMessage = exception.getMessage();

    return new ErrorResponseDto(FORBIDDEN_ERROR_CODE, errorMessage, detailedErrorMessage);
  }
}
