/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched;

import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.function.BiConsumer;

/**
 * Test implementation of {@code ConcurrencyControl}. The main goal is to use this in tests without
 * starting the actor scheduler.
 */
public class TestConcurrencyControl implements ConcurrencyControl {

  @Override
  public <T> void runOnCompletion(
      final ActorFuture<T> future, final BiConsumer<T, Throwable> callback) {
    future.onComplete(callback);
  }

  @Override
  public void submit(final Runnable action) {
    action.run();
  }
}
