/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
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
public class JsonExceptionMapper {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(JsonExceptionMapper.class);

  @ExceptionHandler(JsonMappingException.class)
  public ResponseEntity<String> handleJsonMappingException(final JsonMappingException exception) {
    LOG.debug("Mapping handleJsonMappingException");
    return badRequestResponse();
  }

  @ExceptionHandler(JsonParseException.class)
  public ResponseEntity<String> handleJsonParseException(final JsonParseException exception) {
    LOG.debug("Mapping handleJsonParseException");
    return badRequestResponse();
  }

  private ResponseEntity<String> badRequestResponse() {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN).build();
  }
}
