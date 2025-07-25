/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.client.api.ProblemDetail;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.ProblemException;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
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
    assertThat(result).isEqualTo(expectedMessage);
  }

  @Test
  void testGetErrorMessageWithInvalidStateBrokerException() {
    // Given
    final String reason =
        "Expected to assign user task with key '123L', but it is in state 'ASSIGNING'";
    final ServiceException serviceException =
        ErrorMapper.mapBrokerRejection(
            new BrokerRejection(UserTaskIntent.ASSIGN, 123L, RejectionType.INVALID_STATE, reason));

    // When
    final String result = ErrorHandlingUtils.getErrorMessageFromServiceException(serviceException);

    // Then
    final String expectedMessage =
        """
          { "title": "INVALID_STATE",
            "detail": "%s"
          }
          """
            .formatted(serviceException.getMessage());
    assertThat(result).isEqualTo(expectedMessage);
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
    assertThat(result).isEqualTo(expectedMessage);
  }

  @Test
  void testGetErrorMessageWithBrokerTimeoutException() {
    // Given
    final TimeoutException timeoutException = new TimeoutException("10 SECONDS");
    final ServiceException serviceException = ErrorMapper.mapError(timeoutException);

    // When
    final String result = ErrorHandlingUtils.getErrorMessageFromServiceException(serviceException);

    // Then
    final String expectedMessage =
        """
          { "title": "TASK_PROCESSING_TIMEOUT",
            "detail": "The request timed out while processing the task."
          }
          """;
    assertThat(result).isEqualTo(expectedMessage);
  }

  @Test
  void testGetErrorMessageWithGenericClientException() {
    // Given
    final ClientException genericException = new ClientException("Generic error occurred");

    // When
    final String result = ErrorHandlingUtils.getErrorMessageFromClientException(genericException);

    // Then
    assertThat(result).isEqualTo("Generic error occurred");
  }

  @Test
  void testGetErrorMessageWithGenericBrokerException() {
    // Given
    final ServiceException genericException =
        new ServiceException("Generic error occurred", Status.INTERNAL);

    // When
    final String result = ErrorHandlingUtils.getErrorMessageFromServiceException(genericException);

    // Then
    assertThat(result).isEqualTo("Generic error occurred");
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
    assertThat(result).isEqualTo(expectedMessage);
  }
}
