/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.providers;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
@Slf4j
public class ReportEvaluationExceptionMapper implements ExceptionMapper<ReportEvaluationException> {

  @Override
  public Response toResponse(ReportEvaluationException reportEvaluationException) {
    log.debug("Mapping ReportEvaluationException: {}", reportEvaluationException.getMessage());
    return Response
      .status(Response.Status.INTERNAL_SERVER_ERROR)
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(mapToEvaluationErrorResponseDto(reportEvaluationException))
      .build();
  }

  private ErrorResponseDto mapToEvaluationErrorResponseDto(ReportEvaluationException evaluationException) {
    return new ErrorResponseDto(evaluationException.getMessage(), evaluationException.getReportDefinition());
  }

}
