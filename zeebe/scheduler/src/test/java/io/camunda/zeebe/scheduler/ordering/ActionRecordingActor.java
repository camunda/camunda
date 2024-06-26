/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.ordering;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

public class ActionRecordingActor extends Actor {
  public final List<String> actions = new ArrayList<>();

  protected BiConsumer<Void, Throwable> futureConsumer(final String label) {
    return (v, t) -> {
      actions.add(label);
    };
  }

  protected Runnable runnable(final String label) {
    return () -> {
      actions.add(label);
    };
  }

  protected Callable<Void> callable(final String label) {
    return () -> {
      actions.add(label);
      return null;
    };
  }

  public ActorControl actorControl() {
    return actor;
  }
}
