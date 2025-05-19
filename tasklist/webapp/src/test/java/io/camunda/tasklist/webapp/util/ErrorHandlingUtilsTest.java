/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.camunda.client.api.ProblemDetail;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.ProblemException;
import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class ErrorHandlingUtilsTest {

  @Test
  void testGetErrorMessageWithInvalidStateClientException() {
    // Given
    final ProblemDetail problemDetail =
        new ProblemDetail()
            .setStatus(409)
            .setTitle("INVALID_STATE")
            .setDetail("Task is already in progress.");

    final ProblemException problemException = mock(ProblemException.class);
    when(problemException.details()).thenReturn(problemDetail);

    // When
    final String result = ErrorHandlingUtils.getErrorMessageFromClientException(problemException);

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
  void testGetErrorMessageWithInvalidStateBrokerException() {
    // Given
    final String reason =
        "Expected to assign user task with key '123L', but it is in state 'ASSIGNING'";
    final CamundaBrokerException brokerException =
        new CamundaBrokerException(
            new BrokerRejectionException(
                new BrokerRejection(
                    UserTaskIntent.ASSIGN, 123L, RejectionType.INVALID_STATE, reason)));

    // When
    final String result = ErrorHandlingUtils.getErrorMessageFromBrokerException(brokerException);

    // Then
    final String expectedMessage =
        """
          { "title": "INVALID_STATE",
            "detail": "%s"
          }
          """
            .formatted(reason);
    assertEquals(expectedMessage, result);
  }

  @Test
  void testGetErrorMessageWithClientTimeoutException() {
    // Given
    final SocketTimeoutException socketTimeoutException = new SocketTimeoutException("10 SECONDS");
    final ClientException timeoutException =
        new ClientException("Timeout occurred", new ClientException(socketTimeoutException));

    // When
    final String result = ErrorHandlingUtils.getErrorMessageFromClientException(timeoutException);

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
  void testGetErrorMessageWithBrokerTimeoutException() {
    // Given
    final TimeoutException timeoutException = new TimeoutException("10 SECONDS");
    final CamundaBrokerException brokerException = new CamundaBrokerException(timeoutException);

    // When
    final String result = ErrorHandlingUtils.getErrorMessageFromBrokerException(brokerException);

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
  void testGetErrorMessageWithGenericClientException() {
    // Given
    final ClientException genericException = new ClientException("Generic error occurred");

    // When
    final String result = ErrorHandlingUtils.getErrorMessageFromClientException(genericException);

    // Then
    assertEquals("Generic error occurred", result);
  }

  @Test
  void testGetErrorMessageWithGenericBrokerException() {
    // Given
    final CamundaBrokerException genericException =
        new CamundaBrokerException("Generic error occurred");

    // When
    final String result = ErrorHandlingUtils.getErrorMessageFromBrokerException(genericException);

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
}
