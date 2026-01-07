/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.model.mapper.GatewayErrorMapper;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.Optional;
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
  void testMapProblemToResponse() {
    final ProblemDetail pd =
        GatewayErrorMapper.createProblemDetail(
            HttpStatus.CONFLICT, "Conflict happened", "CONFLICT");
    final ResponseEntity<Object> response = RestErrorMapper.mapProblemToResponse(pd);

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isEqualTo(pd);
  }

  @Test
  void testMapProblemToCompletedResponse() throws Exception {
    final ProblemDetail pd =
        GatewayErrorMapper.createProblemDetail(
            HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", "UNAVAILABLE");
    final ResponseEntity<Object> response = RestErrorMapper.mapProblemToCompletedResponse(pd).get();

    assertThat(response).isNotNull();
    assertThat(response.getBody()).isEqualTo(pd);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  // Sample custom ServiceException for testing
  public static class TestServiceException extends ServiceException {
    public TestServiceException(final String message, final Status status) {
      super(message, status);
    }
  }
}
