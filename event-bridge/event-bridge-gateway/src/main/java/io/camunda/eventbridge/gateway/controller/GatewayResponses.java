/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.protocol.record.RejectionType;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Single source of truth for the gateway's shared HTTP concerns: the content-negotiation media
 * types every controller advertises, the {@code 503} coordinator-unavailable response, and the
 * mapping from coordinator/broker error signals to HTTP status codes.
 */
final class GatewayResponses {

  /** {@code application/json} — humans/Postman, via {@code JsonFormat}. */
  static final String JSON = MediaType.APPLICATION_JSON_VALUE;

  /** {@code application/x-protobuf} — the client SDK, binary. */
  static final String PROTOBUF = "application/x-protobuf";

  private GatewayResponses() {}

  /** The coordinator partition leader was unreachable; the client should retry. */
  static ResponseEntity<?> coordinatorUnavailable() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
  }

  /**
   * Translates a failed coordinator request into an HTTP response. A command the broker
   * <em>rejected</em> surfaces (through the broker client) as a {@link BrokerRejectionException};
   * its {@link RejectionType} maps to a 4xx/5xx status, with the reason as the body. Anything else
   * (the coordinator partition leader was unreachable, a transport error) is a {@code 503} the
   * client retries.
   */
  static ResponseEntity<?> fromCoordinatorError(final Throwable error) {
    if (unwrap(error) instanceof final BrokerRejectionException rejection) {
      final var reason = rejection.getRejection().reason();
      return ResponseEntity.status(statusFor(rejection.getRejection().type())).body(reason);
    }
    return coordinatorUnavailable();
  }

  /**
   * Maps a coordinator error code to the HTTP status the client reacts to.
   *
   * <ul>
   *   <li>{@code 200} — success, or a normal rebalance the client keeps polling through.
   *   <li>{@code 409} — the member is fenced/unknown (stale epoch, or the coordinator failed over
   *       and lost in-memory membership): the client must rejoin.
   *   <li>{@code 400} — malformed request (invalid group id).
   *   <li>{@code 500} — unexpected coordinator error.
   * </ul>
   */
  static HttpStatus statusFor(final CoordinationErrorCode code) {
    return switch (code) {
      case NONE, REBALANCE_IN_PROGRESS -> HttpStatus.OK;
      case UNKNOWN_MEMBER_ID, FENCED_MEMBER_EPOCH, FENCED_MEMBER_ACTIVE, NOT_PARTITION_OWNER ->
          HttpStatus.CONFLICT;
      case INVALID_GROUP_ID -> HttpStatus.BAD_REQUEST;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
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
