/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.ProblemDetail;
import java.net.SocketTimeoutException;
import java.net.URI;
import org.junit.jupiter.api.Test;

class ErrorHandlingUtilsTest {

  @Test
  void testGetErrorMessageWithInvalidStateProblem() {
    // Given
    final long taskKey = 123;
    final ProblemDetail problemDetail =
        new ProblemDetail()
            .status(409)
            .title("INVALID_STATE")
            .detail("Task is already in progress.")
            .instance(URI.create("/v2/user-tasks/123/assignment"));

    final ProblemException problemException = mock(ProblemException.class);
    when(problemException.details()).thenReturn(problemDetail);

    // When
    final String result = ErrorHandlingUtils.getErrorMessage(taskKey, problemException);

    // Then
    final String expectedMessage =
        """
          { "title": "INVALID_STATE",
            "detail": "Task is already in progress.",
            "instance": "/v2/user-tasks/123/assignment"
          }
          """;
    assertEquals(expectedMessage, result);
  }

  @Test
  void testGetErrorMessageWithTimeoutException() {
    // Given
    final long taskKey = 123;
    final SocketTimeoutException socketTimeoutException = new SocketTimeoutException("10 SECONDS");
    final Throwable timeoutException =
        new ClientException("Timeout occurred", new ClientException(socketTimeoutException));

    // When
    final String result = ErrorHandlingUtils.getErrorMessage(taskKey, timeoutException);

    // Then
    final String expectedMessage =
        """
          { "title": "TASK_PROCESSING_TIMEOUT",
            "detail": "The request timed out while processing the task.",
            "instance": "/v2/user-tasks/123/assignment"
          }
          """;
    assertEquals(expectedMessage, result);
  }

  @Test
  void testGetErrorMessageWithGenericException() {
    // Given
    final long taskKey = 123;
    final ClientException genericException = new ClientException("Generic error occurred");

    // When
    final String result = ErrorHandlingUtils.getErrorMessage(taskKey, genericException);

    // Then
    assertEquals("Generic error occurred", result);
  }

  @Test
  void testCreateErrorMessage() {
    // Given
    final String title = "Internal Server Error";
    final String detail = "An unexpected error occurred.";
    final URI instance = URI.create("/v2/user-tasks/unknown");

    // When
    final String result = ErrorHandlingUtils.createErrorMessage(title, detail, instance);

    // Then
    final String expectedMessage =
        """
          { "title": "Internal Server Error",
            "detail": "An unexpected error occurred.",
            "instance": "/v2/user-tasks/unknown"
          }
          """;
    assertEquals(expectedMessage, result);
  }

  @Test
  void testIsCausedByTimeoutExceptionWithTimeoutException() {
    // Given
    final SocketTimeoutException socketTimeoutException = new SocketTimeoutException("10 SECONDS");
    final Throwable timeoutException =
        new ClientException("Timeout occurred", new ClientException(socketTimeoutException));

    // When
    final boolean result = ErrorHandlingUtils.isCausedByTimeoutException(timeoutException);

    // Then
    assertTrue(result);
  }

  @Test
  void testIsCausedByTimeoutExceptionWithoutTimeoutException() {
    // Given
    final Exception genericException = new Exception("Generic error occurred");

    // When
    final boolean result = ErrorHandlingUtils.isCausedByTimeoutException(genericException);

    // Then
    assertFalse(result);
  }

  @Test
  void testIsCausedByTimeoutExceptionWithNullThrowable() {

    // When
    ErrorHandlingUtils.isCausedByTimeoutException(null);
    final boolean result = false;

    // Then
    assertFalse(result);
  }
}
