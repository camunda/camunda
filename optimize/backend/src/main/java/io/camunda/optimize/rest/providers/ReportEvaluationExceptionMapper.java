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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
public class ReportEvaluationExceptionMapper implements ExceptionMapper<ReportEvaluationException> {
  private final LocalizationService localizationService;

  public ReportEvaluationExceptionMapper(@Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @Override
  public Response toResponse(ReportEvaluationException reportEvaluationException) {
    log.debug("Mapping ReportEvaluationException: {}", reportEvaluationException.getMessage());
    return Response.status(Response.Status.BAD_REQUEST)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(mapToEvaluationErrorResponseDto(reportEvaluationException))
        .build();
  }

  private ErrorResponseDto mapToEvaluationErrorResponseDto(
      ReportEvaluationException evaluationException) {
    String errorCode = evaluationException.getErrorCode();
    String errorMessage = localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    String detailedErrorMessage = evaluationException.getMessage();

    return new ErrorResponseDto(
        errorCode, errorMessage, detailedErrorMessage, evaluationException.getReportDefinition());
  }
}
