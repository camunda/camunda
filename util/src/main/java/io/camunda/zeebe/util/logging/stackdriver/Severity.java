/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.logging.stackdriver;

/**
 * The severity of the event described in a log entry, expressed as one of the standard severity
 * levels listed below. For your reference, the levels are assigned the listed numeric values. The
 * effect of using numeric values other than those listed is undefined.
 *
 * <p>https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry#logseverity
 */
public enum Severity {
  DEFAULT(0),
  DEBUG(100),
  INFO(200),
  NOTICE(300),
  WARNING(400),
  ERROR(500),
  CRITICAL(600),
  ALERT(700),
  EMERGENCY(800);

  private final int level;

  Severity(final int level) {
    this.level = level;
  }

  public int getLevel() {
    return level;
  }
}
