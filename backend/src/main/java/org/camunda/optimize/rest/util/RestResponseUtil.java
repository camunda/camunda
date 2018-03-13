package org.camunda.optimize.rest.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.service.exceptions.ReportEvaluationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

public class RestResponseUtil {
  public static final String REPORT_DEFINITION = "\"reportDefinition\" : ";
  private static ObjectMapper OBJECT_MAPPER;
  private static final Logger logger = LoggerFactory.getLogger(RestResponseUtil.class);

  public static Response buildServerErrorResponse(Throwable e, ObjectMapper objectMapper) {
    if (OBJECT_MAPPER == null) {
      OBJECT_MAPPER = objectMapper;
    }

    if (NotAuthorizedException.class.equals(e.getClass())) {
      return buildServerAuthenticationErrorResponse(e.getMessage());
    }
    if (NotFoundException.class.equals(e.getClass())) {
      return buildServerNotFoundErrorResponse(e.getMessage());
    }
    if (ReportEvaluationException.class.equals(e.getClass())) {
      return buildReportEvaluationErrorResponse((ReportEvaluationException)e);
    }

    return buildServerErrorResponse(e.getMessage());
  }

  private static Response buildReportEvaluationErrorResponse(ReportEvaluationException error) {
    String reportDetail;
    String reportDetailKeyValue = "";
    try {
      reportDetail = OBJECT_MAPPER.writeValueAsString(error.getReportDefinition());
      reportDetailKeyValue = REPORT_DEFINITION + reportDetail;
    } catch (JsonProcessingException e) {
      logger.error("can't serialize report definition", e);
    }

    return Response
      .serverError()
      .entity(
        "{ " +
          "\"errorMessage\" : \"" + error.getMessage() + "\", " +
          reportDetailKeyValue +
          "}"
      )
      .build();
  }

  private static Response buildServerNotFoundErrorResponse(String message) {
    return Response
        .serverError()
        .status(Response.Status.NOT_FOUND)
        .entity("{ \"errorMessage\" : \"" + message + "\"}").build();
  }

  private static Response buildServerAuthenticationErrorResponse(String message) {
    return Response
        .serverError()
        .status(Response.Status.UNAUTHORIZED)
        .entity("{ \"errorMessage\" : \"" + message + "\"}").build();
  }

  public static Response buildServerErrorResponse(String message) {
    return Response
        .serverError()
        .entity("{ \"errorMessage\" : \"" + message + "\"}").build();
  }
}
