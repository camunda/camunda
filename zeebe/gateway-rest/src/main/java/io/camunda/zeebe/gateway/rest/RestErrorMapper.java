/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

public class RestErrorMapper {

  private static final Logger REST_GATEWAY_LOGGER =
      LoggerFactory.getLogger("io.camunda.zeebe.gateway.rest");

  public static <T> Optional<ResponseEntity<T>> getResponse(
      final BrokerResponse<?> brokerResponse,
      final Throwable error,
      final Function<BrokerRejection, ProblemDetail> rejectionMapper) {
    return Optional.ofNullable(error)
        .map(e -> mapErrorToProblem(e, rejectionMapper))
        .or(() -> mapBrokerErrorToProblem(brokerResponse))
        .or(() -> mapRejectionToProblem(brokerResponse, rejectionMapper))
        .map(RestErrorMapper::mapProblemToResponse);
  }

  private static ProblemDetail mapErrorToProblem(
      final Throwable error, final Function<BrokerRejection, ProblemDetail> rejectionMapper) {
    if (error == null) {
      return null;
    }
    return switch (error) {
      case final BrokerErrorException bee:
        yield mapBrokerErrorToProblem(error, bee.getError());
      case final BrokerRejectionException bre:
        REST_GATEWAY_LOGGER.trace(
            "Expected to handle REST request, but the broker rejected it", error);
        yield rejectionMapper.apply(bre.getRejection());
      case final ExecutionException ee:
        yield mapErrorToProblem(ee.getCause(), rejectionMapper);
      case final CompletionException ce:
        yield mapErrorToProblem(ce.getCause(), rejectionMapper);
      default:
        REST_GATEWAY_LOGGER.error(
            "Expected to handle REST request, but an unexpected error occurred", error);
        yield createProblemDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Unexpected error occurred during the request processing: " + error.getMessage(),
            error.getClass().getName());
    };
  }

  private static Optional<ProblemDetail> mapBrokerErrorToProblem(
      final BrokerResponse<?> brokerResponse) {
    if (brokerResponse.isError()) {
      return Optional.ofNullable(mapBrokerErrorToProblem(null, brokerResponse.getError()));
    }
    return Optional.empty();
  }

  private static ProblemDetail mapBrokerErrorToProblem(
      final Throwable rootError, final BrokerError error) {
    if (error == null) {
      return null;
    }
    String message = error.getMessage();
    final String title = error.code().name();

    return switch (error.getCode()) {
      case PROCESS_NOT_FOUND -> createProblemDetail(HttpStatus.NOT_FOUND, message, title);
      case RESOURCE_EXHAUSTED -> {
        REST_GATEWAY_LOGGER.trace("Target broker is currently overloaded: {}", error, rootError);
        yield createProblemDetail(HttpStatus.TOO_MANY_REQUESTS, message, title);
      }
      case PARTITION_LEADER_MISMATCH -> {
        // return 503 to indicate to the user that retrying might solve the issue, as this
        // is usually a transient issue
        REST_GATEWAY_LOGGER.trace(
            "Target broker was not the leader of the partition: {}", error, rootError);
        yield createProblemDetail(HttpStatus.SERVICE_UNAVAILABLE, message, title);
      }
      default -> {
        // all the following are for cases where retrying (with the same gateway) is not
        // expected
        // to solve anything
        REST_GATEWAY_LOGGER.error(
            "Expected to handle REST request, but received an internal error from broker: {}",
            error,
            rootError);
        message =
            String.format(
                "Received an unexpected error from the broker, code: %s, message: %s",
                error.getCode(), error.getMessage());
        yield createProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, message, title);
      }
    };
  }

  private static Optional<ProblemDetail> mapRejectionToProblem(
      final BrokerResponse<?> brokerResponse,
      final Function<BrokerRejection, ProblemDetail> rejectionMapper) {
    if (brokerResponse.isRejection()) {
      return Optional.ofNullable(rejectionMapper.apply(brokerResponse.getRejection()));
    }
    return Optional.empty();
  }

  public static ProblemDetail createProblemDetail(
      final HttpStatusCode status, final String detail, final String title) {
    final var problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    return problemDetail;
  }

  public static <T> ResponseEntity<T> mapProblemToResponse(final ProblemDetail problemDetail) {
    return ResponseEntity.of(problemDetail)
        .headers(httpHeaders -> httpHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON))
        .build();
  }
}
