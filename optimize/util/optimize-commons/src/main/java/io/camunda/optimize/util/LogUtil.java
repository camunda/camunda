/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.util;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

public class LogUtil extends CompositeConverter<ILoggingEvent> {

  /** Escape any NLF (newline function) and Backspace to prevent log injection attacks. */
  @Override
  protected String transform(final ILoggingEvent iLoggingEvent, final String s) {
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

  public static String sanitizeLogMessage(final String input) {
    return input.replaceAll("[\n|\r|\t]", "_");
  }
}
