/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.providers;

import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class ReportEvaluationExceptionMapper implements ExceptionMapper<ReportEvaluationException> {
  private static final Logger logger = LoggerFactory.getLogger(ReportEvaluationExceptionMapper.class);

  @Override
  public Response toResponse(ReportEvaluationException reportEvaluationException) {
    logger.debug("Mapping ReportEvaluationException: {}", reportEvaluationException.getMessage());
    return Response
      // as this is a valid state in the system and the user has to take action to fix the evaluation result
      .status(Response.Status.INTERNAL_SERVER_ERROR)
      .entity(mapToEvaluationErrorResponseDto(reportEvaluationException))
      .build();
  }

  private ErrorResponseDto mapToEvaluationErrorResponseDto(ReportEvaluationException evaluationException) {
      return new ErrorResponseDto(evaluationException.getMessage(), evaluationException.getReportDefinition());
  }

}
