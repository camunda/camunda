/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.logging;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.NullAppender;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * Captures one logger's events so a test can assert on what a component logged rather than on what
 * it threw.
 *
 * <p>Raising the logger's level is part of the job: with no log4j2 configuration on the test
 * classpath the root level drops anything below ERROR before an appender ever sees it. The previous
 * level is restored on {@link #close()}, so a later test in the same fork neither inherits this
 * one's verbosity nor depends on having run after it.
 *
 * <pre>{@code
 * try (final var logs = LogCapturer.capturing(MyComponent.class, Level.WARN)) {
 *   component.doSomethingItOnlyWarnsAbout();
 *   assertThat(logs.messagesAt(Level.WARN)).anySatisfy(m -> assertThat(m).contains("expected"));
 * }
 * }</pre>
 */
public final class LogCapturer implements AutoCloseable {

  /** Keeps concurrently open capturers from removing each other's appender by name. */
  private static final AtomicLong APPENDER_SEQUENCE = new AtomicLong();

  private final RecordingAppender appender;
  private final String loggerName;

  /** Never null: {@link org.apache.logging.log4j.Logger#getLevel()} answers the effective level. */
  private final Level previousLevel;

  private LogCapturer(final String loggerName, final Level level) {
    this.loggerName = loggerName;
    appender =
        new RecordingAppender(
            NullAppender.createAppender(
                "LogCapturer-" + APPENDER_SEQUENCE.incrementAndGet() + "-" + loggerName));
    appender.start();
    previousLevel = LogManager.getLogger(loggerName).getLevel();
    Configurator.setLevel(loggerName, level);
    final LoggerContext context = (LoggerContext) LogManager.getContext(false);
    context.getConfiguration().getLoggerConfig(loggerName).addAppender(appender, level, null);
    context.updateLoggers();
  }

  /** Captures {@code loggerName}'s events at {@code level} and above. */
  public static LogCapturer capturing(final String loggerName, final Level level) {
    return new LogCapturer(loggerName, level);
  }

  /** Captures the events of the logger {@code type} logs to, at {@code level} and above. */
  public static LogCapturer capturing(final Class<?> type, final Level level) {
    return new LogCapturer(type.getName(), level);
  }

  /** Every captured message, formatted, in the order it was logged. */
  public List<String> messages() {
    return appender.getAppendedEvents().stream()
        .map(event -> event.getMessage().getFormattedMessage())
        .toList();
  }

  /** The captured messages logged at exactly {@code level}. */
  public List<String> messagesAt(final Level level) {
    return appender.getAppendedEvents().stream()
        .filter(event -> event.getLevel() == level)
        .map(event -> event.getMessage().getFormattedMessage())
        .toList();
  }

  /** Whether any captured message contains {@code substring}. */
  public boolean contains(final String substring) {
    return messages().stream().anyMatch(message -> message.contains(substring));
  }

  @Override
  public void close() {
    final LoggerContext context = (LoggerContext) LogManager.getContext(false);
    context.getConfiguration().getLoggerConfig(loggerName).removeAppender(appender.getName());
    Configurator.setLevel(loggerName, previousLevel);
    context.updateLoggers();
    appender.stop();
  }
}
