/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.utils;

import io.atomix.utils.concurrent.Scheduled;
import io.atomix.utils.concurrent.ThreadContext;
import java.time.Duration;

public class InlineThreadContext implements ThreadContext {

  @Override
  public void checkThread() {}

  @Override
  public Scheduled schedule(
      final Duration initialDelay, final Duration interval, final Runnable callback) {
    throw new UnsupportedOperationException("schedule");
  }

  @Override
  public void execute(final Runnable runnable) {
    runnable.run();
  }
}
