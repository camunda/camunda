/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.zeebe.util.LogUtil;
import io.zeebe.util.logging.stackdriver.Severity;
import io.zeebe.util.logging.stackdriver.StackdriverLogEntry;
import io.zeebe.util.logging.stackdriver.StackdriverLogEntryBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.OutputStreamAppender;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.util.Constants;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;

public final class StackdriverLayoutTest {
  private static final org.slf4j.Logger LOGGER =
      LoggerFactory.getLogger(StackdriverLayoutTest.class);
  private static final ObjectReader OBJECT_READER = new ObjectMapper().reader();
  private static final String SERVICE = "test-service";
  private static final String VERSION = "test-version";

  @Rule public JUnitSoftAssertions softly = new JUnitSoftAssertions();

  private Logger logger;
  private PipedInputStream source;
  private PipedOutputStream sink;
  private RecordingAppender appender;

  @Before
  public void before() throws IOException {
    sink = new PipedOutputStream();
    source = new PipedInputStream(512 * 1024);
    logger = (Logger) LogManager.getLogger();

    final var layout =
        StackdriverLayout.newBuilder().setServiceName(SERVICE).setServiceVersion(VERSION).build();
    appender = createAndStartAppender(layout, sink);
    logger.addAppender(appender);

    sink.connect(source);
    logger.setLevel(Level.DEBUG);
  }

  @After
  public void tearDown() {
    try {
      source.close();
    } catch (final IOException e) {
      LOGGER.error("Failed to close source input stream", e);
    }

    try {
      sink.close();
    } catch (final IOException e) {
      LOGGER.error("Failed to close sink output stream", e);
    }

    logger.removeAppender(appender);
    appender.stop();
  }

  @Test
  public void shouldWriteTraceMessage() throws IOException {
    // given
    logger.setLevel(Level.TRACE);

    // when
    logger.trace("Trace message");

    // then
    final var jsonMap = readLoggedEvent();
    softly.assertThat(jsonMap).containsEntry("severity", Severity.DEBUG.name());
  }

  @Test
  public void shouldWriteDebugMessages() throws IOException {
    // given
    logger.setLevel(Level.DEBUG);

    // when
    logger.debug("Debug message");

    // then
    final var jsonMap = readLoggedEvent();
    softly.assertThat(jsonMap).containsEntry("severity", Severity.DEBUG.name());
  }

  @Test
  public void shouldWriteInfoMessage() throws IOException {
    // given
    logger.setLevel(Level.INFO);

    // when
    logger.info("Info message");

    // then
    final var jsonMap = readLoggedEvent();
    softly.assertThat(jsonMap).containsEntry("severity", Severity.INFO.name());
  }

  @Test
  public void shouldWriteWarningMessage() throws IOException {
    // given
    logger.setLevel(Level.WARN);

    // when
    logger.warn("Info message");

    // then
    final var jsonMap = readLoggedEvent();
    softly.assertThat(jsonMap).containsEntry("severity", Severity.WARNING.name());
  }

  @Test
  public void shouldWriteErrorMessageWithoutException() throws IOException {
    // given
    logger.setLevel(Level.ERROR);

    // when
    logger.error("Error message {}", 1);
    final var stackTrace = new IllegalStateException("").getStackTrace();
    final var source = stackTrace[0];

    // then
    final var jsonMap = readLoggedEvent();
    softly
        .assertThat(jsonMap)
        .containsEntry("severity", Severity.ERROR.name())
        .containsEntry("message", "Error message 1")
        .containsEntry("@type", StackdriverLogEntry.ERROR_REPORT_TYPE)
        .hasEntrySatisfying(
            "context",
            context ->
                softly
                    .assertThat(context)
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .hasEntrySatisfying(
                        StackdriverLogEntryBuilder.ERROR_REPORT_LOCATION_CONTEXT_KEY,
                        reportLocation ->
                            softly
                                .assertThat(reportLocation)
                                .asInstanceOf(InstanceOfAssertFactories.MAP)
                                .containsEntry("filePath", source.getFileName())
                                .containsEntry("functionName", source.getMethodName())
                                .containsEntry("lineNumber", source.getLineNumber() - 1)))
        .doesNotContainKey("exception");
  }

  @Test
  public void shouldWriteErrorMessageWithException() throws IOException {
    // given
    logger.setLevel(Level.ERROR);

    // when
    final var exception = new ThrowableProxy(new IllegalStateException("Failed"));
    logger.error("Error message", exception.getThrowable());

    // then
    final var jsonMap = readLoggedEvent();
    softly
        .assertThat(jsonMap)
        .containsEntry("severity", Severity.ERROR.name())
        .containsEntry("message", "Error message")
        .containsEntry("@type", StackdriverLogEntry.ERROR_REPORT_TYPE)
        .containsEntry("exception", exception.getExtendedStackTraceAsString())
        .hasEntrySatisfying(
            "context",
            context ->
                softly
                    .assertThat(context)
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .doesNotContainKey(
                        StackdriverLogEntryBuilder.ERROR_REPORT_LOCATION_CONTEXT_KEY));
  }

  @Test
  public void shouldContainFormattedMessage() throws IOException {
    // given
    final var expectedMessage = "This is an ultra message";

    // when
    logger.info("This is an {} message", "ultra");

    // then
    final var jsonMap = readLoggedEvent();
    softly.assertThat(jsonMap).containsEntry("message", expectedMessage);
  }

  @Test
  public void shouldContainTime() throws IOException {
    // when
    logger.info("This is a message");

    // then
    final var jsonMap = readLoggedEvent();
    final var event = appender.getAppendedEvents().get(0);
    final var timestampSeconds = ((Number) jsonMap.get("timestampSeconds")).longValue();
    final var timestampNanos = ((Number) jsonMap.get("timestampNanos")).longValue();

    softly.assertThat(timestampSeconds).isEqualTo(event.getInstant().getEpochSecond());
    softly.assertThat(timestampNanos).isEqualTo(event.getInstant().getNanoOfSecond());
  }

  @Test
  public void shouldTerminateAllEntriesWithALineSeparator() throws IOException {
    // given
    final var lineSeparator = System.lineSeparator();

    // when
    logger.info("Should be terminated with a line separator");

    // then
    final var rawOutput = source.readNBytes(source.available());
    softly.assertThat(new String(rawOutput)).endsWith(lineSeparator);
  }

  @Test
  public void shouldContainSourceLocation() throws IOException {
    // when
    logger.info("Message");
    final var stackTrace = new IllegalStateException("").getStackTrace();
    final var source = stackTrace[0];

    // then
    final var jsonMap = readLoggedEvent();
    softly
        .assertThat(jsonMap)
        .hasEntrySatisfying(
            "logging.googleapis.com/sourceLocation",
            sourceLocation ->
                softly
                    .assertThat(sourceLocation)
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .containsEntry("file", source.getFileName())
                    .containsEntry("function", source.getMethodName())
                    .containsEntry("line", source.getLineNumber() - 1));
  }

  @Test
  public void shouldContainServiceContext() throws IOException {
    // when
    logger.info("Message");

    // then
    final var jsonMap = readLoggedEvent();
    softly
        .assertThat(jsonMap)
        .hasEntrySatisfying(
            "serviceContext",
            serviceContext ->
                softly
                    .assertThat(serviceContext)
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .containsEntry("service", SERVICE)
                    .containsEntry("version", VERSION));
  }

  @Test
  public void shouldContainContext() throws IOException {
    // given
    final var expectedContext = Map.of("foo", "bar", "baz", "boz");

    // when
    LogUtil.doWithMDC(expectedContext, () -> logger.info("Message"));

    // then
    final var jsonMap = readLoggedEvent();
    softly
        .assertThat(jsonMap)
        .hasEntrySatisfying(
            "context",
            context ->
                softly
                    .assertThat(context)
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .containsAllEntriesOf(expectedContext));
  }

  @Test
  public void shouldContainThreadInfo() throws IOException {
    // given
    final var currentThread = Thread.currentThread();
    logger.setLevel(Level.INFO);

    // when
    logger.info("Message");

    // then
    final var jsonMap = readLoggedEvent();
    softly
        .assertThat(jsonMap)
        .hasEntrySatisfying(
            "context",
            context ->
                softly
                    .assertThat(context)
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .containsEntry("threadName", currentThread.getName())
                    .containsEntry(
                        "threadId",
                        (int) currentThread.getId()) // Jackson will parse small numbers as integers
                    .containsEntry("threadPriority", currentThread.getPriority()));
  }

  @Test
  public void shouldContainLogger() throws IOException {
    // given
    logger.setLevel(Level.INFO);

    // when
    logger.info("Message");

    // then
    final var jsonMap = readLoggedEvent();
    softly
        .assertThat(jsonMap)
        .hasEntrySatisfying(
            "context",
            context ->
                softly
                    .assertThat(context)
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .containsEntry("loggerName", logger.getName()));
  }

  @Test
  public void shouldWriteLargeMessageWithoutOverflow() throws IOException {
    // given
    final var largeMessageSize = Constants.ENCODER_BYTE_BUFFER_SIZE * 2;
    final var largeMessage = "a".repeat(largeMessageSize);

    // when
    logger.info(largeMessage);

    // then
    final var jsonMap = readLoggedEvent();
    softly.assertThat(jsonMap).containsEntry("message", largeMessage);
  }

  private Map<String, Object> readLoggedEvent() throws IOException {
    return OBJECT_READER.withValueToUpdate(new HashMap<String, Object>()).readValue(source);
  }

  private RecordingAppender createAndStartAppender(
      final Layout<?> layout, final OutputStream logTarget) {
    final OutputStreamAppender appender =
        OutputStreamAppender.createAppender(layout, null, logTarget, "test", false, false);
    final RecordingAppender recordingAppender = new RecordingAppender(appender);
    recordingAppender.start();
    return recordingAppender;
  }
}
