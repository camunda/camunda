/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.util;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;

public class LogUtil extends LogEventPatternConverter {

  private final String transformParameter;

  protected LogUtil(final String name, final String style, final String transformParameter) {
    super(name, style);
    this.transformParameter = transformParameter;
  }

  @PluginFactory
  public static LogUtil newInstance(final String[] options) {
    final String transformParameter = (options != null && options.length > 0) ? options[0] : "";
    return new LogUtil("LogUtil", "custom", transformParameter);
  }

  @Override
  public void format(final LogEvent event, final StringBuilder toAppendTo) {
    // Equivalent transformation logic that was in your `transform` method
    final String transformedMessage = transform(event, transformParameter);
    toAppendTo.append(transformedMessage);
  }

  public static String sanitizeLogMessage(final String message) {
    return message
        // newline
        .replace("\n", "\\n")
        // carriage return
        .replace("\r", "\\r")
        // backspace
        .replace("\b", "\\b")
        // next line
        .replace("\u0085", "\\u0085")
        // vertical tab
        .replace("\u000B", "\\u000B")
        // form feed
        .replace("\u000C", "\\u000C")
        // line separator
        .replace("\u2028", "\\u2028")
        // paragraph separator
        .replace("\u2029", "\\u2029");
  }

  /** Escape any NLF (newline function) and Backspace to prevent log injection attacks. */
  protected String transform(final LogEvent iLoggingEvent, final String s) {
    return s
        // newline
        .replace("\n", "\\n")
        // carriage return
        .replace("\r", "\\r")
        // backspace
        .replace("\b", "\\b")
        // next line
        .replace("\u0085", "\\u0085")
        // vertical tab
        .replace("\u000B", "\\u000B")
        // form feed
        .replace("\u000C", "\\u000C")
        // line separator
        .replace("\u2028", "\\u2028")
        // paragraph separator
        .replace("\u2029", "\\u2029");
  }
}
