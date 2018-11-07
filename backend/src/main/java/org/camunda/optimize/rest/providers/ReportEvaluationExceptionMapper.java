package org.camunda.optimize.rest.providers;

import org.camunda.optimize.dto.optimize.rest.EvaluationErrorResponseDto;
import org.camunda.optimize.service.exceptions.ReportEvaluationException;
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

  private EvaluationErrorResponseDto mapToEvaluationErrorResponseDto(ReportEvaluationException reportEvaluationException) {
    return new EvaluationErrorResponseDto(
      reportEvaluationException.getMessage(),
      reportEvaluationException.getReportDefinition()
    );
  }

}
