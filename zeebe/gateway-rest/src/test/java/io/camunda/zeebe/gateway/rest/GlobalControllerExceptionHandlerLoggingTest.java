/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.test.util.logging.RecordingAppender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

final class GlobalControllerExceptionHandlerLoggingTest {

  private final RecordingAppender recorder = new RecordingAppender();
  private final GlobalControllerExceptionHandler handler = new GlobalControllerExceptionHandler();
  private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

  private Logger restLogger;
  private Level previousLevel;

  @BeforeEach
  void setUp() {
    restLogger = (Logger) LogManager.getLogger(Loggers.REST_LOGGER.getName());
    previousLevel = restLogger.getLevel();
    Configurator.setLevel(restLogger, Level.DEBUG);
    recorder.start();
    restLogger.addAppender(recorder);
  }

  @AfterEach
  void tearDown() {
    restLogger.removeAppender(recorder);
    recorder.stop();
    Configurator.setLevel(restLogger, previousLevel);
  }

  @Test
  void shouldNotLogStackTraceForClientError() throws Exception {
    // given
    final var exception4xxType =
        new HttpMessageNotReadableException(
            "JSON parse error: Unexpected value 'startTime'", emptyInputMessage());

    // when
    final var response = handler.handleException(exception4xxType, request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(recorder.getAppendedEvents())
        .singleElement()
        .extracting(LogEvent::getThrown)
        .isNull(); // verifies the exception is not logged
  }

  @Test
  void shouldLogStackTraceForServerError() throws Exception {
    // given
    final var exception5xxType =
        new HttpMessageNotWritableException(
            "Could not write JSON", new IllegalStateException("unexpected"));

    // when
    final var response = handler.handleException(exception5xxType, request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(recorder.getAppendedEvents())
        .singleElement()
        .extracting(LogEvent::getThrown)
        .isNotNull(); // verifies the exception logged
  }

  private static MockHttpInputMessage emptyInputMessage() {
    return new MockHttpInputMessage(new byte[0]);
  }
}
