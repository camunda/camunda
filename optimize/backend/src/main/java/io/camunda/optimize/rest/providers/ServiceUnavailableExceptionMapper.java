/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import com.fasterxml.jackson.core.JsonParseException;
import io.camunda.optimize.rest.exceptions.ServiceUnavailableException;
import org.slf4j.Logger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE) // This mapper takes precedence over GenericExceptionMapper
public class ServiceUnavailableExceptionMapper {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ServiceUnavailableExceptionMapper.class);

  @ExceptionHandler(ServiceUnavailableException.class)
  public ResponseEntity<String> handleServiceUnavailableException(
      final JsonParseException exception) {
    LOG.debug("Mapping ServiceUnavailableException");
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.TEXT_PLAIN)
        .build();
  }
}
