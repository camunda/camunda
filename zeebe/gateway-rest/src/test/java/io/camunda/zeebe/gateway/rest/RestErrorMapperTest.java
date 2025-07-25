/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

public class RestErrorMapperTest {

  @Test
  void testGetResponseWithServiceException() {
    final Throwable error = new TestServiceException("Something went wrong", Status.NOT_FOUND);
    final Optional<ResponseEntity<Object>> response = RestErrorMapper.getResponse(error);
    assertThat(response.isPresent()).isTrue();

    final ProblemDetail pd = (ProblemDetail) response.get().getBody();
    assertThat(pd).isNotNull();
    assertThat(pd.getTitle()).isEqualTo("NOT_FOUND");
    assertThat(pd.getDetail()).isEqualTo("Something went wrong");
    assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  @Test
  void testGetResponseWithNullError() {
    final Optional<ResponseEntity<Object>> response = RestErrorMapper.getResponse(null);
    assertThat(response.isEmpty()).isTrue();
  }

  @Test
  void testMapErrorToProblemWithWrappedServiceException() {
    final Throwable error =
        new CompletionException(new TestServiceException("Wrapped error", Status.FORBIDDEN));
    final ProblemDetail pd = RestErrorMapper.mapErrorToProblem(error);

    assertThat(pd).isNotNull();
    assertThat(pd.getTitle()).isEqualTo("FORBIDDEN");
    assertThat(pd.getDetail()).isEqualTo("Wrapped error");
    assertThat(pd.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void testMapErrorToProblemWithUnknownError() {
    final Throwable error = new RuntimeException("Generic failure");
    final ProblemDetail pd = RestErrorMapper.mapErrorToProblem(error);

    assertThat(pd).isNotNull();
    assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(pd.getDetail().contains("Generic failure")).isTrue();
    assertThat(pd.getTitle()).isEqualTo(RuntimeException.class.getName());
  }

  @Test
  void testCreateProblemDetail() {
    final ProblemDetail pd =
        RestErrorMapper.createProblemDetail(
            HttpStatus.BAD_REQUEST, "Bad request", "INVALID_ARGUMENT");
    assertThat(pd.getTitle()).isEqualTo("INVALID_ARGUMENT");
    assertThat(pd.getDetail()).isEqualTo("Bad request");
    assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  void testMapProblemToResponse() {
    final ProblemDetail pd =
        RestErrorMapper.createProblemDetail(HttpStatus.CONFLICT, "Conflict happened", "CONFLICT");
    final ResponseEntity<Object> response = RestErrorMapper.mapProblemToResponse(pd);

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isEqualTo(pd);
  }

  @Test
  void testMapProblemToCompletedResponse() throws Exception {
    final ProblemDetail pd =
        RestErrorMapper.createProblemDetail(
            HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", "UNAVAILABLE");
    final ResponseEntity<Object> response = RestErrorMapper.mapProblemToCompletedResponse(pd).get();

    assertThat(response).isNotNull();
    assertThat(response.getBody()).isEqualTo(pd);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void testMapStatus() {
    assertThat(RestErrorMapper.mapStatus(Status.ABORTED)).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(RestErrorMapper.mapStatus(Status.UNAVAILABLE))
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(RestErrorMapper.mapStatus(Status.RESOURCE_EXHAUSTED))
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(RestErrorMapper.mapStatus(Status.UNKNOWN))
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(RestErrorMapper.mapStatus(Status.FORBIDDEN)).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(RestErrorMapper.mapStatus(Status.NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(RestErrorMapper.mapStatus(Status.UNAUTHORIZED)).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(RestErrorMapper.mapStatus(Status.ALREADY_EXISTS)).isEqualTo(HttpStatus.CONFLICT);
    assertThat(RestErrorMapper.mapStatus(Status.INVALID_ARGUMENT))
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(RestErrorMapper.mapStatus(Status.DEADLINE_EXCEEDED))
        .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
  }

  // Sample custom ServiceException for testing
  public static class TestServiceException extends ServiceException {
    public TestServiceException(final String message, final Status status) {
      super(message, status);
    }
  }
}
