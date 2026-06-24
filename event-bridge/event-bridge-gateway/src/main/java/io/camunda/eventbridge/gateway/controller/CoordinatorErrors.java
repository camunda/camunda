/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.protocol.record.RejectionType;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Translates a failed coordinate request into an HTTP response. A command the broker
 * <em>rejected</em> surfaces (through the broker client) as a {@link BrokerRejectionException}; its
 * {@link RejectionType} maps to a 4xx/5xx status, with the reason as the body. Anything else (the
 * coordinator partition leader was unreachable, a transport error) is a {@code 503} the client
 * retries.
 */
final class CoordinatorErrors {

  private CoordinatorErrors() {}

  static ResponseEntity<Object> toResponse(final Throwable error) {
    if (unwrap(error) instanceof final BrokerRejectionException rejection) {
      final var reason = rejection.getRejection().reason();
      return ResponseEntity.status(statusFor(rejection.getRejection().type()))
          .body((Object) reason);
    }
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
  }

  private static HttpStatus statusFor(final RejectionType type) {
    return switch (type) {
      case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case ALREADY_EXISTS, INVALID_STATE -> HttpStatus.CONFLICT;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }

  /** Peels the {@link CompletionException}/{@link ExecutionException} wrappers a future may add. */
  private static Throwable unwrap(final Throwable error) {
    var cause = error;
    while ((cause instanceof CompletionException || cause instanceof ExecutionException)
        && cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause;
  }
}
