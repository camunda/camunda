/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class OptimizeExceptionHandling {

  // this is needed because MVC would throw 500 by default, while we want to throw 400
  // in case of validation exception.
  @ExceptionHandler(OptimizeValidationException.class)
  public ResponseEntity<String> handleOptimizeValidationException(
      final OptimizeValidationException ex) {
    return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
  }
}
