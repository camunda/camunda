/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import java.time.Duration;

public final class DurationUtil {

  private DurationUtil() {}

  /** Returns the larger of two durations. */
  public static Duration max(final Duration a, final Duration b) {
    return a.compareTo(b) >= 0 ? a : b;
  }

  /** Returns the smaller of two durations. */
  public static Duration min(final Duration a, final Duration b) {
    return a.compareTo(b) <= 0 ? a : b;
  }
}
