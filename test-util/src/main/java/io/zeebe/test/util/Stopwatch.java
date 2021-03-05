/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Not-thread-safe utility to take timestamps and print a summary of time elapsed between
 * timestamps. Useful for analyzing where tests spend runtime.
 */
public final class Stopwatch {
  protected final List<Checkpoint> checkpoints = new ArrayList<>();

  public void record(final String checkpoint) {
    System.out.println(checkpoint);
    final Checkpoint c = new Checkpoint();
    c.name = checkpoint;
    c.timestamp = System.currentTimeMillis();
    checkpoints.add(c);
  }

  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("\nStopwatch results:\n");

    if (checkpoints.size() >= 2) {
      for (int i = 1; i < checkpoints.size(); i++) {
        final Checkpoint from = checkpoints.get(i - 1);
        final Checkpoint to = checkpoints.get(i);

        sb.append("From ");
        sb.append(from.name);
        sb.append(" to ");
        sb.append(to.name);
        sb.append(": ");
        sb.append(Duration.ofMillis(to.timestamp - from.timestamp));
        sb.append("\n");
      }
    } else {
      sb.append("Needs at least two checkpoints");
    }

    sb.append("\n");

    return sb.toString();
  }

  protected static class Checkpoint {
    String name;
    long timestamp;
  }
}
