/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched.ordering;

import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorControl;
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
