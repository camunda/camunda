/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim.error;

import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.exceptions.ScimException;
import io.camunda.gateway.scim.ScimJson;
import io.camunda.gateway.scim.controller.ScimDiscoveryController;
import io.camunda.gateway.scim.controller.ScimGroupController;
import io.camunda.gateway.scim.controller.ScimUserController;
import io.camunda.service.exception.ServiceException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Renders every failure of the SCIM controllers as a SCIM error response. It takes precedence over
 * the REST gateway's global handler, which would render a problem detail instead.
 */
@RestControllerAdvice(
    assignableTypes = {
      ScimUserController.class,
      ScimGroupController.class,
      ScimDiscoveryController.class
    })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScimExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ScimExceptionHandler.class);
  private static final MediaType SCIM_MEDIA_TYPE = MediaType.parseMediaType(ScimJson.MEDIA_TYPE);

  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> handle(final Exception exception) {
    final var scimException = toScimException(unwrap(exception));
    if (scimException.getScimError().getStatus() >= 500) {
      LOG.error("Failed to handle SCIM request", exception);
    } else {
      LOG.debug("SCIM request failed", exception);
    }
    return ResponseEntity.status(scimException.getScimError().getStatus())
        .contentType(SCIM_MEDIA_TYPE)
        .body(ScimJson.write(scimException.getScimError()));
  }

  private static Throwable unwrap(final Throwable throwable) {
    if ((throwable instanceof CompletionException || throwable instanceof ExecutionException)
        && throwable.getCause() != null) {
      return unwrap(throwable.getCause());
    }
    return throwable;
  }

  private static ScimException toScimException(final Throwable error) {
    if (error instanceof final ScimException scimException) {
      return scimException;
    }
    if (error instanceof final ServiceException serviceException) {
      return fromServiceException(serviceException);
    }
    if (error instanceof HttpMessageNotReadableException) {
      return BadRequestException.invalidSyntax("Request body cannot be read");
    }
    if (error instanceof final ErrorResponse errorResponse
        && errorResponse.getStatusCode().is4xxClientError()) {
      final var status = errorResponse.getStatusCode().value();
      final var scimType = status == 400 ? BadRequestException.INVALID_VALUE : null;
      return new ScimException(status, scimType, errorResponse.getBody().getDetail());
    }
    return new ScimException(
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        null,
        "Unexpected error occurred during the request processing");
  }

  private static ScimException fromServiceException(final ServiceException exception) {
    final var message = exception.getMessage();
    return switch (exception.getStatus()) {
      case ALREADY_EXISTS -> new ScimException(409, BadRequestException.UNIQUENESS, message);
      case INVALID_STATE -> new ScimException(409, null, message);
      case NOT_FOUND -> new ScimException(404, null, message);
      case INVALID_ARGUMENT -> new ScimException(400, BadRequestException.INVALID_VALUE, message);
      case FORBIDDEN, UNAUTHORIZED -> new ScimException(403, null, message);
      case NOT_ACCEPTABLE -> new ScimException(406, null, message);
      case UNAVAILABLE, RESOURCE_EXHAUSTED -> new ScimException(503, null, message);
      case ABORTED -> new ScimException(502, null, message);
      case DEADLINE_EXCEEDED -> new ScimException(504, null, message);
      case INTERNAL, UNKNOWN -> new ScimException(500, null, message);
    };
  }
}
