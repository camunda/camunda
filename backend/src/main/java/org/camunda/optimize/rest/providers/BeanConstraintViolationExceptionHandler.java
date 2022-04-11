/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import com.google.common.collect.Iterables;
import org.camunda.optimize.dto.optimize.rest.ValidationErrorResponseDto;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Provider
public class BeanConstraintViolationExceptionHandler implements ExceptionMapper<ConstraintViolationException> {

  public static final String THE_REQUEST_BODY_WAS_INVALID = "The request body was invalid.";
  private static final Pattern ARG_PATTERN = Pattern.compile("arg\\d");

  @Override
  public Response toResponse(final ConstraintViolationException throwable) {
    final List<ValidationErrorResponseDto.ValidationError> validationErrors = throwable
      .getConstraintViolations()
      .stream()
      .map(constraintViolation -> new ValidationErrorResponseDto.ValidationError(
        extractPropertyName(constraintViolation),
        constraintViolation.getMessage()
      ))
      .collect(Collectors.toList());
    return Response
      .status(Response.Status.BAD_REQUEST)
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(new ValidationErrorResponseDto(THE_REQUEST_BODY_WAS_INVALID, validationErrors))
      .build();
  }

  private String extractPropertyName(final ConstraintViolation<?> constraintViolation) {
    String propertyName = null;
    final int pathLength = Iterables.size(constraintViolation.getPropertyPath());
    if (pathLength > 2) {
      final List<Path.Node> propertyNodePath = StreamSupport
        // skipping first which is the controller's method name
        .stream(Iterables.skip(constraintViolation.getPropertyPath(), 1).spliterator(), false)
        // skipping any generic argument nodes as we don't use a name provider
        // @formatter:off
        // https://beanvalidation.org/1.1/spec/#constraintdeclarationvalidationprocess-methodlevelconstraints-definingparameterconstraints-namingparameters
        // @formatter:on
        .filter(node -> node != null && node.getName() != null && !ARG_PATTERN.matcher(node.getName()).matches())
        .collect(Collectors.toList());

      // check if argument is list
      propertyName = propertyNodePath.stream().map(node -> {
        if (node.isInIterable()) {
          return String.format("element[%d].%s", node.getIndex(), node);
        } else {
          return node.toString();
        }
      }).collect(Collectors.joining("."));
    }
    return Optional.ofNullable(propertyName).orElseGet(() -> constraintViolation.getPropertyPath().toString());
  }

}
