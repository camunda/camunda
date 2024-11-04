/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.rest;

import com.fasterxml.jackson.core.JsonParseException;
import io.atomix.cluster.messaging.MessagingException;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.PartitionNotFoundException;
import io.camunda.zeebe.broker.client.api.RequestRetriesExhaustedException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.cmd.ConcurrentRequestException;
import io.camunda.zeebe.msgpack.spec.MsgpackException;
import io.netty.channel.ConnectTimeoutException;
import java.net.ConnectException;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
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
      case final MsgpackException mpe:
        final var mpeMsg =
            "Expected to handle REST API request, but messagepack property was invalid";
        REST_GATEWAY_LOGGER.debug(mpeMsg, mpe);
        yield createProblemDetail(HttpStatus.BAD_REQUEST, mpeMsg, mpe.getClass().getName());
      case final JsonParseException jpe:
        final var jpeMsg = "Expected to handle REST API request, but JSON property was invalid";
        REST_GATEWAY_LOGGER.debug(jpeMsg, jpe);
        yield createProblemDetail(HttpStatus.BAD_REQUEST, jpeMsg, jpe.getClass().getName());
      case final IllegalArgumentException iae:
        final var iaeMsg = "Expected to handle REST API request, but JSON property was invalid";
        REST_GATEWAY_LOGGER.debug(iaeMsg, iae);
        yield createProblemDetail(HttpStatus.BAD_REQUEST, iaeMsg, iae.getClass().getName());
      case final RequestRetriesExhaustedException rree:
        final var rreeMsg =
            "Expected to handle REST API request, but all retries have been exhausted";
        REST_GATEWAY_LOGGER.trace(rreeMsg, rree);
        yield createProblemDetail(HttpStatus.TOO_MANY_REQUESTS, rreeMsg, rree.getClass().getName());
      case final TimeoutException te:
        final var teMsg =
            "Expected to handle REST API request, but request timed out between gateway and broker";
        REST_GATEWAY_LOGGER.debug(teMsg, te);
        yield createProblemDetail(HttpStatus.GATEWAY_TIMEOUT, teMsg, te.getClass().getName());
      case final MessagingException.ConnectionClosed cc:
        final var ccMsg =
            "Expected to handle REST API request, but the connection was cut prematurely with the broker; "
                + "the request may or may not have been accepted, and may not be safe to retry";
        REST_GATEWAY_LOGGER.warn(ccMsg, cc);
        yield createProblemDetail(HttpStatus.BAD_GATEWAY, ccMsg, cc.getClass().getName());
      case final ConnectTimeoutException cte:
        final var cteMsg =
            "Expected to handle REST API request, but a connection timeout exception occurred";
        REST_GATEWAY_LOGGER.warn(cteMsg, cte);
        yield createProblemDetail(HttpStatus.SERVICE_UNAVAILABLE, cteMsg, cte.getClass().getName());
      case final ConnectException ce:
        final var ceMsg =
            "Expected to handle REST API request, but there was a connection error with one of the brokers";
        REST_GATEWAY_LOGGER.warn(ceMsg, ce);
        yield createProblemDetail(HttpStatus.SERVICE_UNAVAILABLE, ceMsg, ce.getClass().getName());
      case final PartitionNotFoundException pnfe:
        final var pnfeMsg =
            "Expected to handle REST API request, but request could not be delivered";
        REST_GATEWAY_LOGGER.debug(pnfeMsg, pnfe);
        yield createProblemDetail(
            HttpStatus.SERVICE_UNAVAILABLE, pnfeMsg, pnfe.getClass().getName());
      case final ConcurrentRequestException cre:
        final var creMsg = "Expected to handle REST API request, but the request was rejected";
        REST_GATEWAY_LOGGER.debug(creMsg, cre);
        yield createProblemDetail(HttpStatus.SERVICE_UNAVAILABLE, creMsg, cre.getClass().getName());
      default:
        REST_GATEWAY_LOGGER.error(
            "Expected to handle REST API request, but an unexpected error occurred", error);
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
