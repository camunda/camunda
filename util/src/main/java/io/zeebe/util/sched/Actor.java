/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched;

import java.util.function.Consumer;

public abstract class Actor {
  protected final ActorControl actor = new ActorControl(this);

  public String getName() {
    return getClass().getName();
  }

  protected void onActorStarting() {
    // setup
  }

  protected void onActorStarted() {
    // logic
  }

  protected void onActorClosing() {
    // tear down
  }

  protected void onActorClosed() {
    // what ever
  }

  protected void onActorCloseRequested() {
    // notification that timers, conditions, etc. will no longer trigger from now on
  }

  public static Actor wrap(Consumer<ActorControl> r) {
    return new Actor() {
      @Override
      public String getName() {
        return r.toString();
      }

      @Override
      protected void onActorStarted() {
        r.accept(actor);
      }
    };
  }
}
