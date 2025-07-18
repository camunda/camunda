/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    assertTrue(response.isPresent());

    final ProblemDetail pd = (ProblemDetail) response.get().getBody();
    assertNotNull(pd);
    assertEquals("NOT_FOUND", pd.getTitle());
    assertEquals("Something went wrong", pd.getDetail());
    assertEquals(HttpStatus.NOT_FOUND.value(), pd.getStatus());
  }

  @Test
  void testGetResponseWithNullError() {
    final Optional<ResponseEntity<Object>> response = RestErrorMapper.getResponse(null);
    assertTrue(response.isEmpty());
  }

  @Test
  void testMapErrorToProblemWithWrappedServiceException() {
    final Throwable error =
        new CompletionException(new TestServiceException("Wrapped error", Status.FORBIDDEN));
    final ProblemDetail pd = RestErrorMapper.mapErrorToProblem(error);

    assertNotNull(pd);
    assertEquals("FORBIDDEN", pd.getTitle());
    assertEquals("Wrapped error", pd.getDetail());
    assertEquals(HttpStatus.FORBIDDEN.value(), pd.getStatus());
  }

  @Test
  void testMapErrorToProblemWithUnknownError() {
    final Throwable error = new RuntimeException("Generic failure");
    final ProblemDetail pd = RestErrorMapper.mapErrorToProblem(error);

    assertNotNull(pd);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), pd.getStatus());
    assertTrue(pd.getDetail().contains("Generic failure"));
    assertEquals(RuntimeException.class.getName(), pd.getTitle());
  }

  @Test
  void testCreateProblemDetail() {
    final ProblemDetail pd =
        RestErrorMapper.createProblemDetail(
            HttpStatus.BAD_REQUEST, "Bad request", "INVALID_ARGUMENT");
    assertEquals("INVALID_ARGUMENT", pd.getTitle());
    assertEquals("Bad request", pd.getDetail());
    assertEquals(HttpStatus.BAD_REQUEST.value(), pd.getStatus());
  }

  @Test
  void testMapProblemToResponse() {
    final ProblemDetail pd =
        RestErrorMapper.createProblemDetail(HttpStatus.CONFLICT, "Conflict happened", "CONFLICT");
    final ResponseEntity<Object> response = RestErrorMapper.mapProblemToResponse(pd);

    assertNotNull(response);
    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    assertEquals(pd, response.getBody());
  }

  @Test
  void testMapProblemToCompletedResponse() throws Exception {
    final ProblemDetail pd =
        RestErrorMapper.createProblemDetail(
            HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", "UNAVAILABLE");
    final ResponseEntity<Object> response = RestErrorMapper.mapProblemToCompletedResponse(pd).get();

    assertNotNull(response);
    assertEquals(pd, response.getBody());
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
  }

  @Test
  void testMapStatus() {
    assertEquals(HttpStatus.BAD_GATEWAY, RestErrorMapper.mapStatus(Status.ABORTED));
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, RestErrorMapper.mapStatus(Status.UNAVAILABLE));
    assertEquals(
        HttpStatus.SERVICE_UNAVAILABLE, RestErrorMapper.mapStatus(Status.RESOURCE_EXHAUSTED));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, RestErrorMapper.mapStatus(Status.UNKNOWN));
    assertEquals(HttpStatus.FORBIDDEN, RestErrorMapper.mapStatus(Status.FORBIDDEN));
    assertEquals(HttpStatus.NOT_FOUND, RestErrorMapper.mapStatus(Status.NOT_FOUND));
    assertEquals(HttpStatus.UNAUTHORIZED, RestErrorMapper.mapStatus(Status.UNAUTHORIZED));
    assertEquals(HttpStatus.CONFLICT, RestErrorMapper.mapStatus(Status.ALREADY_EXISTS));
    assertEquals(HttpStatus.BAD_REQUEST, RestErrorMapper.mapStatus(Status.INVALID_ARGUMENT));
    assertEquals(HttpStatus.GATEWAY_TIMEOUT, RestErrorMapper.mapStatus(Status.DEADLINE_EXCEEDED));
  }

  // Sample custom ServiceException for testing
  public static class TestServiceException extends ServiceException {
    public TestServiceException(final String message, final Status status) {
      super(message, status);
    }
  }
}
