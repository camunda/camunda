/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequest;
import io.camunda.zeebe.gateway.rest.controller.GatewayObjectMapper;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@ControllerAdvice(annotations = RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JsonProcessingControllerExceptionHandler {

  /**
   * Applied only for {@link UserTaskUpdateRequest} and {@link UserTaskSearchQueryRequest} for
   * strict parsing of Integers, using the {@link GatewayObjectMapper#strictIntegerObjectMapper()}
   * configuration, this handler formats the error in a predictable pattern.
   */
  @ExceptionHandler(JsonProcessingException.class)
  public ResponseEntity<ProblemDetail> handleJsonProcessingException(
      final JsonProcessingException ex) {
    final String errorMessage = "Invalid input: '%s' for field '%s' registered as %s";
    if (ex instanceof final InvalidFormatException ife) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              errorMessage.formatted(
                  ife.getValue(), extractPathReference(ife), ife.getTargetType().getName()),
              INVALID_ARGUMENT.name());
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    }
    final ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    return ResponseEntity.of(problemDetail).build();
  }

  private String extractPathReference(final InvalidFormatException ex) {
    final Pattern pathPattern = Pattern.compile("\\[\"(.*?)\"]");
    final var matcher = pathPattern.matcher(ex.getPathReference());
    final StringBuilder path = new StringBuilder();
    while (matcher.find()) {
      path.append(matcher.group(1)).append(".");
    }
    path.deleteCharAt(path.length() - 1);
    return path.toString();
  }
}
