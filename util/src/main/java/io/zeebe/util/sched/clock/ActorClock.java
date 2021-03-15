/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched.clock;

import io.zeebe.util.sched.ActorThread;

public interface ActorClock {
  boolean update();

  long getTimeMillis();

  long getNanosSinceLastMillisecond();

  long getNanoTime();

  static ActorClock current() {
    final ActorThread current = ActorThread.current();
    return current != null ? current.getClock() : null;
  }

  static long currentTimeMillis() {
    final ActorClock clock = current();
    return clock != null ? clock.getTimeMillis() : System.currentTimeMillis();
  }
}
