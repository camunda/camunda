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
import org.slf4j.Logger;

@Provider
public class ReportEvaluationExceptionMapper implements ExceptionMapper<ReportEvaluationException> {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ReportEvaluationExceptionMapper.class);
  private final LocalizationService localizationService;

  public ReportEvaluationExceptionMapper(@Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @Override
  public Response toResponse(final ReportEvaluationException reportEvaluationException) {
    log.debug("Mapping ReportEvaluationException: {}", reportEvaluationException.getMessage());
    return Response.status(Response.Status.BAD_REQUEST)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(mapToEvaluationErrorResponseDto(reportEvaluationException))
        .build();
  }

  private ErrorResponseDto mapToEvaluationErrorResponseDto(
      final ReportEvaluationException evaluationException) {
    final String errorCode = evaluationException.getErrorCode();
    final String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    final String detailedErrorMessage = evaluationException.getMessage();

    return new ErrorResponseDto(
        errorCode, errorMessage, detailedErrorMessage, evaluationException.getReportDefinition());
  }
}
