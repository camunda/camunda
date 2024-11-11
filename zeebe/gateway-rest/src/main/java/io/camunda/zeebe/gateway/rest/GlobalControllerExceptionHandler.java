/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice(annotations = RestController.class)
public class GlobalControllerExceptionHandler extends ResponseEntityExceptionHandler {

  private static final String REQUEST_BODY_MISSING_EXCEPTION_MESSAGE =
      "Required request body is missing";
  private static final String INVALID_ENUM_VALUE_EXCEPTION_MESSAGE = "Invalid Enum value";

  @Override
  protected ProblemDetail createProblemDetail(
      final Exception ex,
      final HttpStatusCode status,
      final String defaultDetail,
      final String detailMessageCode,
      final Object[] detailMessageArguments,
      final WebRequest request) {

    final String detail;

    if (isRequestBodyMissing(ex)) {
      // Replace detail "Failed to read request"
      // with "Required request body is missing"
      // for proper exception tracing
      detail = REQUEST_BODY_MISSING_EXCEPTION_MESSAGE;
    } else if (isUnknownEnumError(ex)) {
      final var httpMessageNotReadableException = (HttpMessageNotReadableException) ex;
      detail = Objects.requireNonNull(httpMessageNotReadableException.getRootCause()).getMessage();
    } else {
      detail = defaultDetail;
    }

    return super.createProblemDetail(
        ex, status, detail, detailMessageCode, detailMessageArguments, request);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      @NonNull final Exception ex,
      final Object body,
      @NonNull final HttpHeaders headers,
      @NonNull final HttpStatusCode statusCode,
      @NonNull final WebRequest request) {
    Loggers.REST_LOGGER.debug(ex.getMessage(), ex);
    return super.handleExceptionInternal(ex, body, headers, statusCode, request);
  }

  private boolean isRequestBodyMissing(final Exception ex) {
    if (ex instanceof final HttpMessageNotReadableException exception) {
      final var exceptionMessage = exception.getMessage();
      if (exceptionMessage != null
          && exceptionMessage.startsWith(REQUEST_BODY_MISSING_EXCEPTION_MESSAGE)) {
        return true;
      }
    }

    return false;
  }

  private boolean isUnknownEnumError(final Exception ex) {
    if (ex instanceof final HttpMessageNotReadableException exception) {
      final var exceptionMessage = exception.getMessage();

      return exceptionMessage != null
          && exceptionMessage.contains(INVALID_ENUM_VALUE_EXCEPTION_MESSAGE);
    }

    return false;
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleAllExceptions(
      final Exception ex, final HttpServletRequest request) {
    Loggers.REST_LOGGER.debug(ex.getMessage(), ex);
    final ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.of(problemDetail).build();
  }
}
