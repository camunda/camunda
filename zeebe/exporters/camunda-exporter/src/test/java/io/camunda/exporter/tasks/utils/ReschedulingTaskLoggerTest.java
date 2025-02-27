/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.utils;

import io.camunda.exporter.tasks.util.ReschedulingTaskLogger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

public class ReschedulingTaskLoggerTest {

  @Test
  void shouldLogEvery10ThReoccurringError() {
    // given
    final Logger loggerMock = Mockito.spy(Logger.class);
    final ReschedulingTaskLogger logger = new ReschedulingTaskLogger(loggerMock);
    final String errorMessage = "error message";
    final Exception exception = new RuntimeException(errorMessage);

    // when
    for (int i = 1; i <= 20; i++) {
      logger.logError(errorMessage, exception);
    }

    // then - every 10th error is logged on error level, others on debug
    final var inOrder = Mockito.inOrder(loggerMock);

    inOrder.verify(loggerMock, Mockito.timeout(5_000).times(1)).error(errorMessage, exception);
    inOrder.verify(loggerMock, Mockito.timeout(5_000).times(9)).debug(errorMessage);

    inOrder.verify(loggerMock, Mockito.timeout(5_000).times(1)).error(errorMessage, exception);
    inOrder.verify(loggerMock, Mockito.timeout(5_000).times(9)).debug(errorMessage);
  }

  @Test
  void shouldLogEveryNewError() {
    // given
    final Logger loggerMock = Mockito.spy(Logger.class);
    final String errorMessage1 = "failure1";
    final String errorMessage2 = "failure2";
    final Exception exception1 = new RuntimeException(errorMessage1);
    final Exception exception2 = new RuntimeException(errorMessage2);
    final ReschedulingTaskLogger logger = new ReschedulingTaskLogger(loggerMock);

    // when
    logger.logError(errorMessage1, exception1);
    logger.logError(errorMessage2, exception2);

    // then - when errors are different they are all logged on error level
    final var inOrder = Mockito.inOrder(loggerMock);
    inOrder.verify(loggerMock, Mockito.timeout(5_000).times(1)).error(errorMessage1, exception1);
    inOrder.verify(loggerMock, Mockito.timeout(5_000).times(1)).error(errorMessage2, exception2);
  }
}
