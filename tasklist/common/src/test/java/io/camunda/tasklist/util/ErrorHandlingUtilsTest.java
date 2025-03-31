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

import io.camunda.client.api.ProblemDetail;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.ProblemException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;

class ErrorHandlingUtilsTest {

  @Test
  void testGetErrorMessageWithInvalidStateProblem() {
    // Given
    final ProblemDetail problemDetail =
        new ProblemDetail()
            .setStatus(409)
            .setTitle("INVALID_STATE")
            .setDetail("Task is already in progress.");

    final ProblemException problemException = mock(ProblemException.class);
    when(problemException.details()).thenReturn(problemDetail);

    // When
    final String result = ErrorHandlingUtils.getErrorMessage(problemException);

    // Then
    final String expectedMessage =
        """
          { "title": "INVALID_STATE",
            "detail": "Task is already in progress."
          }
          """;
    assertEquals(expectedMessage, result);
  }

  @Test
  void testGetErrorMessageWithTimeoutException() {
    // Given
    final SocketTimeoutException socketTimeoutException = new SocketTimeoutException("10 SECONDS");
    final Throwable timeoutException =
        new ClientException("Timeout occurred", new ClientException(socketTimeoutException));

    // When
    final String result = ErrorHandlingUtils.getErrorMessage(timeoutException);

    // Then
    final String expectedMessage =
        """
          { "title": "TASK_PROCESSING_TIMEOUT",
            "detail": "The request timed out while processing the task."
          }
          """;
    assertEquals(expectedMessage, result);
  }

  @Test
  void testGetErrorMessageWithGenericException() {
    // Given
    final ClientException genericException = new ClientException("Generic error occurred");

    // When
    final String result = ErrorHandlingUtils.getErrorMessage(genericException);

    // Then
    assertEquals("Generic error occurred", result);
  }

  @Test
  void testCreateErrorMessage() {
    // Given
    final String title = "Internal Server Error";
    final String detail = "An unexpected error occurred.";

    // When
    final String result = ErrorHandlingUtils.createErrorMessage(title, detail);

    // Then
    final String expectedMessage =
        """
          { "title": "Internal Server Error",
            "detail": "An unexpected error occurred."
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
