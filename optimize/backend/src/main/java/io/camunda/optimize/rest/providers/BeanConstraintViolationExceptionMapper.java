/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import com.google.common.collect.Iterables;
import io.camunda.optimize.dto.optimize.rest.ValidationErrorResponseDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class BeanConstraintViolationExceptionMapper {

  public static final String THE_REQUEST_BODY_WAS_INVALID = "The request body was invalid.";
  private static final Pattern ARG_PATTERN = Pattern.compile("arg\\d");

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ValidationErrorResponseDto> handleConstraintViolationException(
      final ConstraintViolationException throwable) {
    final List<ValidationErrorResponseDto.ValidationError> validationErrors =
        throwable.getConstraintViolations().stream()
            .map(
                constraintViolation ->
                    new ValidationErrorResponseDto.ValidationError(
                        extractPropertyName(constraintViolation), constraintViolation.getMessage()))
            .collect(Collectors.toList());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ValidationErrorResponseDto(THE_REQUEST_BODY_WAS_INVALID, validationErrors));
  }

  private String extractPropertyName(final ConstraintViolation<?> constraintViolation) {
    String propertyName = null;
    final int pathLength = Iterables.size(constraintViolation.getPropertyPath());
    if (pathLength > 2) {
      final List<Path.Node> propertyNodePath =
          StreamSupport
              // skipping first which is the controller's method name
              .stream(Iterables.skip(constraintViolation.getPropertyPath(), 1).spliterator(), false)
              // skipping any generic argument nodes as we don't use a name provider
              // @formatter:off
              // https://beanvalidation.org/1.1/spec/#constraintdeclarationvalidationprocess-methodlevelconstraints-definingparameterconstraints-namingparameters
              // @formatter:on
              .filter(
                  node ->
                      node != null
                          && node.getName() != null
                          && !ARG_PATTERN.matcher(node.getName()).matches())
              .collect(Collectors.toList());

      // check if argument is list
      propertyName =
          propertyNodePath.stream()
              .map(
                  node -> {
                    if (node.isInIterable()) {
                      return String.format("element[%d].%s", node.getIndex(), node);
                    } else {
                      return node.toString();
                    }
                  })
              .collect(Collectors.joining("."));
    }
    return Optional.ofNullable(propertyName)
        .orElseGet(() -> constraintViolation.getPropertyPath().toString());
  }
}
