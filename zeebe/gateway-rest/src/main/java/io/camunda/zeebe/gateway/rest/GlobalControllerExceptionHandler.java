/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.rest;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

@ControllerAdvice(annotations = RestController.class)
public class GlobalControllerExceptionHandler {

  @ExceptionHandler(ServerWebInputException.class)
  public ResponseEntity<ProblemDetail> handleServerWebInputExceptions(
      final ServerWebInputException ex, final ServerWebExchange context) {
    final ProblemDetail problemDetail = ProblemDetail.forStatus(ex.getStatusCode());
    problemDetail.setTitle(ex.getReason());
    if (ex.getCause() != null) {
      problemDetail.setDetail(ex.getCause().getMessage());
    } else {
      problemDetail.setDetail(ex.getMessage());
    }
    problemDetail.setInstance(URI.create(context.getRequest().getPath().toString()));
    return ResponseEntity.of(problemDetail).build();
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleAllExceptions(
      final Exception ex, final ServerWebExchange context) {
    final ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problemDetail.setInstance(URI.create(context.getRequest().getPath().toString()));
    return ResponseEntity.of(problemDetail).build();
  }
}
