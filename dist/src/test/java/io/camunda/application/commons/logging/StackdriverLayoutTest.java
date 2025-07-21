/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.test.appender.EncodingListAppender;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.apache.logging.log4j.layout.template.json.JsonTemplateLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;

@SuppressWarnings("LoggingSimilarMessage")
final class StackdriverLayoutTest {

  private static final StackTraceElement DUMMY_LOCATION =
      new StackTraceElement("Foo", "Bar", "Foo.java", 1);
  private static final String LOG_MESSAGE = "Hello World!";

  private Logger logger;
  private ListAppender appender;
  private ListAppender encodingAppender;

  @BeforeEach
  void beforeEach() {
    logger = (Logger) LogManager.getLogger();

    final var configuration = logger.getContext().getConfiguration();
    final var layout =
        JsonTemplateLayout.newBuilder()
            .setConfiguration(configuration)
            .setEventTemplateUri("classpath:logging/StackdriverLayout.json")
            .setLocationInfoEnabled(true)
            .setStackTraceEnabled(true)
            .build();

    appender = new ListAppender("event", null, null, true, false);
    appender.start();
    logger.addAppender(appender);

    encodingAppender = new EncodingListAppender("json", null, layout, true, false);
    encodingAppender.start();
    logger.addAppender(encodingAppender);

    logger.setLevel(Level.TRACE);
  }

  @AfterEach
  void afterEach() {
    if (encodingAppender != null) {
      encodingAppender.stop();
      if (logger != null) {
        logger.removeAppender(encodingAppender);
      }
    }

    if (appender != null) {
      appender.stop();
      if (logger != null) {
        logger.removeAppender(appender);
      }
    }
  }

  @Test
  void shouldLogReportLocationOnErrorWithoutException() {
    // given

    // when
    logger.atError().withLocation(DUMMY_LOCATION).log(LOG_MESSAGE);

    // then
    assertMessageMatchesWithReportLocation();
  }

  @Test
  void shouldLogException() {
    // given
    final var exception = new RuntimeException("Test Exception");

    // when
    logger.atError().withLocation(DUMMY_LOCATION).withThrowable(exception).log(LOG_MESSAGE);

    // then
    assertMessageMatchesWithException(exception);
  }

  @ParameterizedTest
  @MethodSource("severityProvider")
  void shouldMapSeverity(final String expectedSeverity, final Level actualLevel) {
    // given

    // when
    logger.atLevel(actualLevel).withLocation(DUMMY_LOCATION).log(LOG_MESSAGE);

    // then
    assertMessageMatches(expectedSeverity);
  }

  @SuppressWarnings("unused")
  @Test
  void shouldLogContextMap() {
    // given

    // when
    try (final var bar = MDC.putCloseable("foo", "bar");
        final var baz = MDC.putCloseable("baz", "qux")) {
      logger.atDebug().withLocation(DUMMY_LOCATION).log(LOG_MESSAGE);
    }

    // then - note that Log4j2 will print out the context map with keys alphabetically sorted
    assertMessageMatchesWithContext(
        "DEBUG",
        """
        "logging.googleapis.com/labels":{"baz":"qux","foo":"bar"}""");
  }

  @Test
  void shouldLog() {
    // given

    // when
    logger.atDebug().withLocation(DUMMY_LOCATION).log(LOG_MESSAGE);

    // then
    assertMessageMatches("DEBUG");
  }

  private static Stream<Arguments> severityProvider() {
    return Stream.of(
        Arguments.of("DEBUG", Level.TRACE),
        Arguments.of("DEBUG", Level.DEBUG),
        Arguments.of("INFO", Level.INFO),
        Arguments.of("WARNING", Level.WARN));
  }

  private void assertMessageMatches(final String expectedSeverity) {
    assertMessageMatchesWithContext(expectedSeverity, null);
  }

  private void assertMessageMatchesWithContext(
      final String expectedSeverity, final String expectedContext) {
    final var message = appender.getEvents().getFirst();
    final var jsonMessage = encodingAppender.getMessages().getFirst();

    assertThat(jsonMessage)
        .isEqualTo(
            // language=json
            """
        {"timestampSeconds":%d,"timestampNanos":%d,"severity":"%s","message":"Hello World!",\
        "logging.googleapis.com/sourceLocation":{"file":"Foo.java","line":1,\
        "function":"Foo.Bar"},%s\
        "threadContext":{"id":%d,"name":"%s","priority":%d},\
        "loggerName":"io.camunda.application.commons.logging.StackdriverLayoutTest",\
        "serviceContext":{"service":"","version":""}}""",
            message.getInstant().getEpochSecond(),
            message.getInstant().getNanoOfSecond(),
            expectedSeverity,
            expectedContext == null ? "" : expectedContext + ",",
            message.getThreadId(),
            message.getThreadName(),
            message.getThreadPriority());
  }

  private void assertMessageMatchesWithReportLocation() {
    final var message = appender.getEvents().getFirst();
    final var jsonMessage = encodingAppender.getMessages().getFirst();

    assertThat(jsonMessage)
        .isEqualTo(
            // language=json
            """
        {"timestampSeconds":%d,"timestampNanos":%d,"severity":"ERROR","message":"Hello World!",\
        "logging.googleapis.com/sourceLocation":{"file":"Foo.java","line":1,\
        "function":"Foo.Bar"},\
        "threadContext":{"id":%d,"name":"%s","priority":%d},\
        "loggerName":"io.camunda.application.commons.logging.StackdriverLayoutTest",\
        "serviceContext":{"service":"","version":""},\
        "@type":"type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent",\
        "reportLocation":{"filePath":"Foo.java","functionName":"Bar","lineNumber":1}}""",
            message.getInstant().getEpochSecond(),
            message.getInstant().getNanoOfSecond(),
            message.getThreadId(),
            message.getThreadName(),
            message.getThreadPriority());
  }

  private void assertMessageMatchesWithException(final RuntimeException exception) {
    final var message = appender.getEvents().getFirst();
    final var jsonMessage = encodingAppender.getMessages().getFirst();

    // it's hard to generate the actual stack trace in the same format without breaking
    // abstractions, so we just check that the exception message is present in the JSON
    assertThat(jsonMessage)
        .matches(
            Pattern.quote(
                    // language=json
                    """
        {"timestampSeconds":%d,"timestampNanos":%d,"severity":"ERROR","message":"Hello World!",\
        "logging.googleapis.com/sourceLocation":{"file":"Foo.java","line":1,\
        "function":"Foo.Bar"},\
        "threadContext":{"id":%d,"name":"%s","priority":%d},\
        "loggerName":"io.camunda.application.commons.logging.StackdriverLayoutTest",\
        "serviceContext":{"service":"","version":""},\
        "@type":"type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent",\
        "exception":"""
                        .formatted(
                            message.getInstant().getEpochSecond(),
                            message.getInstant().getNanoOfSecond(),
                            message.getThreadId(),
                            message.getThreadName(),
                            message.getThreadPriority()))
                + ".+\"}")
        .contains(exception.getMessage());
  }
}
