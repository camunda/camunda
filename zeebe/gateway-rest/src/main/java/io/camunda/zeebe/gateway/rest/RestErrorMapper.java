/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.service.CamundaServiceException;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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

  public static final Function<BrokerRejection, ProblemDetail> DEFAULT_REJECTION_MAPPER =
      rejection -> {
        final String message =
            String.format(
                "Command '%s' rejected with code '%s': %s",
                rejection.intent(), rejection.type(), rejection.reason());
        final String title = rejection.type().name();
        return switch (rejection.type()) {
          case NOT_FOUND:
            yield RestErrorMapper.createProblemDetail(HttpStatus.NOT_FOUND, message, title);
          case INVALID_STATE:
            yield RestErrorMapper.createProblemDetail(HttpStatus.CONFLICT, message, title);
          case INVALID_ARGUMENT:
          case ALREADY_EXISTS:
            yield RestErrorMapper.createProblemDetail(HttpStatus.BAD_REQUEST, message, title);
          default:
            {
              yield RestErrorMapper.createProblemDetail(
                  HttpStatus.INTERNAL_SERVER_ERROR, message, title);
            }
        };
      };
  private static final Logger REST_GATEWAY_LOGGER =
      LoggerFactory.getLogger("io.camunda.zeebe.gateway.rest");

  public static <T> Optional<ResponseEntity<T>> getResponse(
      final Throwable error, final Function<BrokerRejection, ProblemDetail> rejectionMapper) {
    return Optional.ofNullable(error)
        .map(e -> mapErrorToProblem(e, rejectionMapper))
        .or(() -> mapBrokerErrorToProblem(error))
        .or(() -> mapRejectionToProblem(error, rejectionMapper))
        .map(RestErrorMapper::mapProblemToResponse);
  }

  public static ProblemDetail mapErrorToProblem(
      final Throwable error, final Function<BrokerRejection, ProblemDetail> rejectionMapper) {
    if (error == null) {
      return null;
    }
    return switch (error) {
      case final CamundaServiceException cse:
        yield cse.getCause() != null ? mapErrorToProblem(cse.getCause(), rejectionMapper) : null;
      case final BrokerErrorException bee:
        yield mapBrokerErrorToProblem(bee.getError(), error);
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

  private static Optional<ProblemDetail> mapBrokerErrorToProblem(final Throwable exception) {
    if (!(exception instanceof CamundaServiceException)) {
      return Optional.empty();
    }
    return ((CamundaServiceException) exception)
        .getBrokerError()
        .map(error -> mapBrokerErrorToProblem(error, null));
  }

  private static ProblemDetail mapBrokerErrorToProblem(
      final BrokerError error, final Throwable rootError) {
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
      final Throwable exception, final Function<BrokerRejection, ProblemDetail> rejectionMapper) {
    if (!(exception instanceof CamundaServiceException)) {
      return Optional.empty();
    }
    return ((CamundaServiceException) exception).getBrokerRejection().map(rejectionMapper);
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

  public static CompletableFuture<ResponseEntity<Object>> mapProblemToCompletedResponse(
      final ProblemDetail problemDetail) {
    return CompletableFuture.completedFuture(RestErrorMapper.mapProblemToResponse(problemDetail));
  }

  public static ResponseEntity<Object> mapUserManagementExceptionsToResponse(final Exception e) {
    if (e instanceof IllegalArgumentException) {
      final var problemDetail =
          createProblemDetail(HttpStatus.BAD_REQUEST, e.getMessage(), e.getClass().getName());
      return mapProblemToResponse(problemDetail);
    }

    final var problemDetail =
        createProblemDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e.getClass().getName());
    return mapProblemToResponse(problemDetail);
  }
}
