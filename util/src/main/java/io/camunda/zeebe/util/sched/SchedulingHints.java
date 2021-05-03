/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched;

public final class SchedulingHints {

  public static int ioBound() {
    int hints = 0;

    hints = setIoBound(hints);

    return hints;
  }

  public static int cpuBound(final ActorPriority priority) {
    int hints = 0;

    hints = setCpuBound(hints);
    hints = setPriority(priority.getPriorityClass(), hints);

    return hints;
  }

  public static int setCpuBound(final int hints) {
    return hints & ~1;
  }

  public static int setIoBound(final int hints) {
    return hints | 1;
  }

  public static boolean isCpuBound(final int hints) {
    return (hints & ~1) == hints;
  }

  public static boolean isIoBound(final int hints) {
    return (hints & 1) == hints;
  }

  public static int setPriority(final short priority, final int hints) {
    return hints | (priority << 1);
  }

  public static short getPriority(final int hints) {
    return (short) (hints >> 1);
  }
}
