/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.tasks.util.ReschedulingTaskLogger;
import io.camunda.zeebe.util.logging.RecordingAppender;
import java.util.UUID;
import java.util.stream.IntStream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.slf4j.Log4jLogger;
import org.apache.logging.slf4j.Log4jMarkerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ReschedulingTaskLoggerTest {

  private ReschedulingTaskLogger periodicLogger;
  private final RecordingAppender recorder = new RecordingAppender();
  private Logger log;
  private Log4jLogger logger;

  @BeforeEach
  void beforeEach() {
    log = (Logger) LogManager.getLogger(UUID.randomUUID().toString());
    logger = new Log4jLogger(new Log4jMarkerFactory(), log, log.getName());
    periodicLogger = new ReschedulingTaskLogger(logger);
    Configurator.setLevel(log, Level.DEBUG);

    recorder.start();
    log.addAppender(recorder);
  }

  @AfterEach
  void tearDown() {
    recorder.stop();
    log.removeAppender(recorder);
  }

  @Test
  void shouldLogEvery10ThReoccurringError() {
    // given
    final ReschedulingTaskLogger periodicLogger = new ReschedulingTaskLogger(logger);
    final String errorMessage = "error message {}";
    final Exception exception = new RuntimeException(errorMessage);

    // when
    for (int i = 1; i <= 20; i++) {
      periodicLogger.logError(errorMessage, exception, "someArgument");
    }

    // then - every 10th error is logged on error level, others on debug
    assertThat(recorder.getAppendedEvents()).hasSize(20);

    LogEvent event = recorder.getAppendedEvents().get(0);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getMessage().getFormattedMessage()).isEqualTo("error message someArgument");
    assertThat(event.getThrown()).isEqualTo(exception);

    event = recorder.getAppendedEvents().get(10);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getMessage().getFormattedMessage()).isEqualTo("error message someArgument");
    assertThat(event.getThrown()).isEqualTo(exception);

    IntStream.concat(IntStream.rangeClosed(1, 9), IntStream.rangeClosed(11, 19))
        .forEach(
            i -> {
              final LogEvent event1 = recorder.getAppendedEvents().get(i);
              assertThat(event1.getLevel()).isEqualTo(Level.DEBUG);
              assertThat(event1.getMessage().getFormattedMessage())
                  .isEqualTo("error message someArgument");
            });
  }

  @Test
  void shouldLogEveryNewError() {
    // given
    final ReschedulingTaskLogger periodicLogger = new ReschedulingTaskLogger(logger);

    // when
    periodicLogger.logError("failure {}", new RuntimeException(), "someArgument");
    periodicLogger.logError("otherFailure {}", new RuntimeException(), "someArgument");
    periodicLogger.logError("otherFailure {}", new RuntimeException(), "otherArgument");

    // then - every error is logged on error level
    assertThat(recorder.getAppendedEvents()).hasSize(3);

    LogEvent event = recorder.getAppendedEvents().get(0);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getMessage().getFormattedMessage()).isEqualTo("failure someArgument");

    event = recorder.getAppendedEvents().get(1);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getMessage().getFormattedMessage()).isEqualTo("otherFailure someArgument");

    event = recorder.getAppendedEvents().get(2);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getMessage().getFormattedMessage()).isEqualTo("otherFailure otherArgument");
  }
}
