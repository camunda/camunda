/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import io.camunda.service.exception.ServiceException;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
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
  private static final String REQUEST_BODY_PARSE_EXCEPTION_MESSAGE =
      "Request property [%s] cannot be parsed";
  private static final String INVALID_ENUM_ERROR_MESSAGE =
      "%s for enum field '%s'. Use any of the following values: %s";

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
    } else if (isMismatchedInputError(ex)) {
      final var mismatchedInputException = (MismatchedInputException) ex.getCause();
      final var path =
          mismatchedInputException.getPath().stream()
              .map(Reference::getFieldName)
              .collect(Collectors.joining("."));
      detail = REQUEST_BODY_PARSE_EXCEPTION_MESSAGE.formatted(path);
    } else if (isUnknownEnumError(ex)) {
      final var instantiationException = (ValueInstantiationException) ex.getCause();
      final var options =
          Arrays.stream(instantiationException.getType().getRawClass().getEnumConstants())
              .map(Objects::toString)
              .toList();
      final var field = instantiationException.getPath().getLast().getFieldName();
      detail =
          INVALID_ENUM_ERROR_MESSAGE.formatted(
              ((HttpMessageNotReadableException) ex).getRootCause().getMessage(), field, options);
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

  private boolean isMismatchedInputError(final Exception ex) {
    return ex instanceof HttpMessageNotReadableException
        && ex.getCause() instanceof MismatchedInputException;
  }

  private boolean isUnknownEnumError(final Exception ex) {
    return ex instanceof HttpMessageNotReadableException
        && ex.getCause() instanceof final ValueInstantiationException instantiationException
        && instantiationException.getType().isEnumType();
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleAllExceptions(
      final Exception ex, final HttpServletRequest request) {
    final ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    return RestErrorMapper.mapProblemToResponse(problemDetail);
  }

  @ExceptionHandler(ServiceException.class)
  public ResponseEntity<ProblemDetail> handleServiceException(
      final ServiceException ex, final HttpServletRequest request) {
    return getProblemDetailResponseEntity(ex, request);
  }

  @ExceptionHandler(CompletionException.class)
  public ResponseEntity<ProblemDetail> handleCompletionException(
      final CompletionException ex, final HttpServletRequest request) {
    return getProblemDetailResponseEntity(ex, request);
  }

  private static ResponseEntity<ProblemDetail> getProblemDetailResponseEntity(
      final Exception ex, final HttpServletRequest request) {
    final ProblemDetail problemDetail = RestErrorMapper.mapErrorToProblem(ex);
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    return RestErrorMapper.mapProblemToResponse(problemDetail);
  }
}
