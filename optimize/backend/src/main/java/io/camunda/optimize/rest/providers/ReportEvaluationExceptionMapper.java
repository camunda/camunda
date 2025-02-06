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
import io.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReportEvaluationExceptionMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ReportEvaluationExceptionMapper.class);

  @Autowired private LocalizationService localizationService;

  @ExceptionHandler(ReportEvaluationException.class)
  public ResponseEntity<ErrorResponseDto> handleReportEvaluationException(
      final ReportEvaluationException reportEvaluationException) {
    LOG.info("Mapping ReportEvaluationException");
    final ErrorResponseDto errorResponseDto = getErrorResponseDto(reportEvaluationException);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(errorResponseDto);
  }

  private ErrorResponseDto getErrorResponseDto(final ReportEvaluationException exception) {
    final String errorCode = exception.getErrorCode();
    final String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    final String detailedErrorMessage = exception.getMessage();
    return new ErrorResponseDto(
        errorCode, errorMessage, detailedErrorMessage, exception.getReportDefinition());
  }
}
