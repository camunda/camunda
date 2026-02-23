/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http;

import io.camunda.gateway.protocol.model.CamundaProblemDetail;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

public class GatewayErrorMapper {
  public static final Function<String, Exception> RESOURCE_EXHAUSTED_EXCEPTION_PROVIDER =
      msg -> new ServiceException(msg, ServiceException.Status.RESOURCE_EXHAUSTED);
  public static final Function<String, Throwable> REQUEST_CANCELED_EXCEPTION_PROVIDER =
      RuntimeException::new;

  private static final Logger LOG = LoggerFactory.getLogger(GatewayErrorMapper.class);

  /**
   * Maps a {@link ServiceException} to a {@link ProblemDetail}, unwrapping potential Java
   * concurrent exceptions wrapping the causing {@link ServiceException}. The {@link Status} of the
   * service exception is mapped to an HTTP status.
   *
   * @param error the {@link ServiceException}, can be wrapped in a Java concurrent exception
   * @return the problem detail mapping the service exception content respectively
   */
  public static ProblemDetail mapErrorToProblem(final Throwable error) {
    if (error == null) {
      return null;
    }
    // ServiceExceptions can be wrapped in Java exceptions because they are handled in Java futures
    final var exception = unwrapError(error);
    if (exception instanceof final ServiceException se) {
      return createProblemDetail(mapStatus(se.getStatus()), se.getMessage(), se.getStatus().name());
    } else {
      LOG.error("Expected to handle REST request, but an unexpected error occurred", error);
      return createProblemDetail(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Unexpected error occurred during the request processing: " + exception.getMessage(),
          exception.getClass().getName());
    }
  }

  /**
   * Unwrapping potential Java concurrent exceptions wrapping the real cause.
   *
   * @param throwable the potentially wrapping Java concurrent exception
   * @return the cause of the exception if wrapped, otherwise the exception itself
   */
  public static Throwable unwrapError(final Throwable throwable) {
    // ServiceExceptions can be wrapped in Java exceptions because they are handled in Java futures
    if (throwable instanceof CompletionException || throwable instanceof ExecutionException) {
      return throwable.getCause();
    }
    return throwable;
  }

  /**
   * Creates a {@link ProblemDetail} from a status, detail, and title.
   *
   * @param status the status of the problem
   * @param detail the detail text of the problem
   * @param title the title of the problem
   * @return a problem detail reflecting the provided input
   */
  public static ProblemDetail createProblemDetail(
      final HttpStatusCode status, final String detail, final String title) {
    final var problemDetail = CamundaProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    return problemDetail;
  }

  protected static HttpStatus mapStatus(final Status status) {
    return switch (status) {
      case ABORTED -> HttpStatus.BAD_GATEWAY;
      case UNAVAILABLE, RESOURCE_EXHAUSTED -> HttpStatus.SERVICE_UNAVAILABLE;
      case UNKNOWN, INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
      case FORBIDDEN -> HttpStatus.FORBIDDEN;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
      case ALREADY_EXISTS, INVALID_STATE -> HttpStatus.CONFLICT;
      case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
      case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
    };
  }
}
