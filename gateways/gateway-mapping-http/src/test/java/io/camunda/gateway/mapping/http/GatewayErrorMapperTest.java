/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http;

import static io.camunda.service.exception.ServiceException.Status.ALREADY_EXISTS;
import static io.camunda.service.exception.ServiceException.Status.INTERNAL;
import static io.camunda.service.exception.ServiceException.Status.INVALID_ARGUMENT;
import static io.camunda.service.exception.ServiceException.Status.UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.exception.CamundaSearchException;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public class GatewayErrorMapperTest {

  @Test
  void testMapErrorToProblemWithWrappedServiceException() {
    final Throwable error =
        new CompletionException(new TestServiceException("Wrapped error", Status.FORBIDDEN));
    final ProblemDetail pd = GatewayErrorMapper.mapErrorToProblem(error);

    assertThat(pd).isNotNull();
    assertThat(pd.getTitle()).isEqualTo("FORBIDDEN");
    assertThat(pd.getDetail()).isEqualTo("Wrapped error");
    assertThat(pd.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void testMapErrorToProblemWithUnknownError() {
    final Throwable error = new RuntimeException("Generic failure");
    final ProblemDetail pd = GatewayErrorMapper.mapErrorToProblem(error);

    assertThat(pd).isNotNull();
    assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(pd.getDetail().contains("Generic failure")).isTrue();
    assertThat(pd.getTitle()).isEqualTo(RuntimeException.class.getName());
  }

  @Test
  void testCreateProblemDetail() {
    final ProblemDetail pd =
        GatewayErrorMapper.createProblemDetail(
            HttpStatus.BAD_REQUEST, "Bad request", "INVALID_ARGUMENT");
    assertThat(pd.getTitle()).isEqualTo("INVALID_ARGUMENT");
    assertThat(pd.getDetail()).isEqualTo("Bad request");
    assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  void testMapStatus() {
    assertThat(GatewayErrorMapper.mapStatus(Status.ABORTED)).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(GatewayErrorMapper.mapStatus(Status.UNAVAILABLE))
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(GatewayErrorMapper.mapStatus(Status.RESOURCE_EXHAUSTED))
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(GatewayErrorMapper.mapStatus(Status.UNKNOWN))
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(GatewayErrorMapper.mapStatus(Status.FORBIDDEN)).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(GatewayErrorMapper.mapStatus(Status.NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(GatewayErrorMapper.mapStatus(Status.UNAUTHORIZED))
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(GatewayErrorMapper.mapStatus(Status.ALREADY_EXISTS)).isEqualTo(HttpStatus.CONFLICT);
    assertThat(GatewayErrorMapper.mapStatus(Status.INVALID_ARGUMENT))
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(GatewayErrorMapper.mapStatus(Status.DEADLINE_EXCEEDED))
        .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
  }

  @Test
  void shouldMapCamundaSearchExceptionWhenNoReason() {
    // given
    final CamundaSearchException cse = new CamundaSearchException("No reason");

    // when
    final ProblemDetail problemDetail =
        GatewayErrorMapper.mapErrorToProblem(ErrorMapper.mapSearchError(cse));

    // then
    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(problemDetail.getDetail()).isEqualTo("No reason");
    assertThat(problemDetail.getTitle()).isEqualTo("INTERNAL");
  }

  @Test
  void shouldMapCamundaSearchExceptionWhenNotFound() {
    // given
    final CamundaSearchException cse =
        new CamundaSearchException("Item not found", CamundaSearchException.Reason.NOT_FOUND);

    // when
    final ProblemDetail problemDetail =
        GatewayErrorMapper.mapErrorToProblem(ErrorMapper.mapSearchError(cse));

    // then
    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(problemDetail.getDetail()).isEqualTo("Item not found");
    assertThat(problemDetail.getTitle()).isEqualTo(CamundaSearchException.Reason.NOT_FOUND.name());
  }

  @Test
  void shouldMapCamundaSearchExceptionWhenNotUnique() {
    // given
    final CamundaSearchException cse =
        new CamundaSearchException("Item not unique", CamundaSearchException.Reason.NOT_UNIQUE);

    // when
    final ProblemDetail problemDetail =
        GatewayErrorMapper.mapErrorToProblem(ErrorMapper.mapSearchError(cse));

    // then
    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(problemDetail.getDetail()).isEqualTo("Item not unique");
    assertThat(problemDetail.getTitle()).isEqualTo(ALREADY_EXISTS.name());
  }

  @Test
  void shouldMapCamundaSearchExceptionWhenESClientCannotConnect() {
    // given
    final CamundaSearchException cse =
        new CamundaSearchException(
            "Request failed",
            new ConnectException("No connection"),
            CamundaSearchException.Reason.CONNECTION_FAILED);

    // when
    final ProblemDetail problemDetail =
        GatewayErrorMapper.mapErrorToProblem(ErrorMapper.mapSearchError(cse));

    // then
    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    assertThat(problemDetail.getDetail())
        .isEqualTo("The search client could not connect to the search server");
    assertThat(problemDetail.getTitle()).isEqualTo(UNAVAILABLE.name());
  }

  @Test
  void shouldMapCamundaSearchExceptionWhenESClientIOException() {
    // given
    final CamundaSearchException cse =
        new CamundaSearchException(
            "Request failed",
            new IOException("Generic IO Error"),
            CamundaSearchException.Reason.SEARCH_CLIENT_FAILED);

    // when
    final ProblemDetail problemDetail =
        GatewayErrorMapper.mapErrorToProblem(ErrorMapper.mapSearchError(cse));

    // then
    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(problemDetail.getDetail())
        .isEqualTo("The search client was unable to process the request");
    assertThat(problemDetail.getTitle()).isEqualTo(INTERNAL.name());
  }

  @Test
  void shouldMapCamundaSearchExceptionWhenOSClientCannotConnect() {
    // given
    final CamundaSearchException cse =
        new CamundaSearchException(
            "Request failed",
            new ConnectException("No connection"),
            CamundaSearchException.Reason.CONNECTION_FAILED);

    // when
    final ProblemDetail problemDetail =
        GatewayErrorMapper.mapErrorToProblem(ErrorMapper.mapSearchError(cse));

    // then
    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    assertThat(problemDetail.getDetail())
        .isEqualTo("The search client could not connect to the search server");
    assertThat(problemDetail.getTitle()).isEqualTo(UNAVAILABLE.name());
  }

  @Test
  void shouldMapCamundaSearchExceptionWhenOSClientIOException() {
    // given
    final CamundaSearchException cse =
        new CamundaSearchException(
            "Request failed",
            new IOException("Generic IO Error"),
            CamundaSearchException.Reason.SEARCH_CLIENT_FAILED);

    // when
    final ProblemDetail problemDetail =
        GatewayErrorMapper.mapErrorToProblem(ErrorMapper.mapSearchError(cse));

    // then
    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(problemDetail.getDetail())
        .isEqualTo("The search client was unable to process the request");
    assertThat(problemDetail.getTitle()).isEqualTo(INTERNAL.name());
  }

  @Test
  void shouldMapCamundaSearchExceptionWhenInvalidArgument() {
    // given
    final CamundaSearchException cse =
        new CamundaSearchException(
            "Invalid argument provided",
            new IllegalArgumentException("Illegal argument"),
            CamundaSearchException.Reason.INVALID_ARGUMENT);

    // when
    final ProblemDetail problemDetail =
        GatewayErrorMapper.mapErrorToProblem(ErrorMapper.mapSearchError(cse));

    // then
    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problemDetail.getDetail()).isEqualTo("Invalid argument provided");
    assertThat(problemDetail.getTitle()).isEqualTo(INVALID_ARGUMENT.name());
  }

  // Sample custom ServiceException for testing
  public static class TestServiceException extends ServiceException {
    public TestServiceException(final String message, final Status status) {
      super(message, status);
    }
  }
}
