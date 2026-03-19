/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Global exception handler for the Event Bridge gateway.
 *
 * <p>Maps common Spring MVC exceptions (missing/malformed request parameters, unreadable request
 * bodies) to structured {@link ErrorResponse} JSON bodies. Uncaught exceptions are caught as a
 * last resort and returned as {@code 500 Internal Server Error} to avoid leaking stack traces.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** Missing required query parameter — e.g. {@code groupId} absent on a poll request. */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingParam(
      final MissingServletRequestParameterException ex) {
    return ResponseEntity.badRequest()
        .body(
            new ErrorResponse(
                "INVALID_REQUEST",
                "Required parameter '" + ex.getParameterName() + "' is missing"));
  }

  /**
   * Type mismatch on a request parameter — e.g. {@code fromPosition=abc} when a {@code long} is
   * expected.
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(
      final MethodArgumentTypeMismatchException ex) {
    final String param = ex.getName();
    final Class<?> expected = ex.getRequiredType();
    final String typeName = expected != null ? expected.getSimpleName() : "unknown";
    return ResponseEntity.badRequest()
        .body(
            new ErrorResponse(
                "INVALID_REQUEST",
                "Parameter '" + param + "' must be a valid " + typeName));
  }

  /**
   * Unreadable or malformed request body — e.g. invalid JSON or Base64 in a publish request that
   * was not caught at the controller level.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadableBody(
      final HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest()
        .body(new ErrorResponse("INVALID_REQUEST", "Request body is missing or malformed"));
  }

  /**
   * HTTP method not supported — e.g. {@code POST} sent to an endpoint that only accepts {@code
   * GET}.
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
      final HttpRequestMethodNotSupportedException ex) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(
            new ErrorResponse(
                "METHOD_NOT_ALLOWED",
                "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint"));
  }

  /**
   * Catch-all for unexpected exceptions. Logs the full stack trace at ERROR level and returns
   * {@code 500 Internal Server Error} without exposing internal details to the caller.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(final Exception ex) {
    LOG.error("Unexpected error processing Event Bridge request", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
  }
}
