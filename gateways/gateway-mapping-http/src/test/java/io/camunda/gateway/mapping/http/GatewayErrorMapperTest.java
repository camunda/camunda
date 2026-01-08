/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
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

  // Sample custom ServiceException for testing
  public static class TestServiceException extends ServiceException {
    public TestServiceException(final String message, final Status status) {
      super(message, status);
    }
  }
}
