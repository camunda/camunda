/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched;

import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.Loggers;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class Actor implements CloseableSilently, AsyncClosable, ConcurrencyControl {

  public static final String ACTOR_PROP_NAME = "actor-name";
  public static final String ACTOR_PROP_PARTITION_ID = "partitionId";

  private static final int MAX_CLOSE_TIMEOUT = 300;
  protected final ActorControl actor = new ActorControl(this);
  private Map<String, String> context;

  /**
   * Should be overwritten by sub classes to add more context where the actor is run.
   *
   * @return the context of the actor
   */
  protected Map<String, String> createContext() {
    // return an modifiable map in order to simplify sub class implementation
    final var baseContext = new HashMap<String, String>();
    baseContext.put(ACTOR_PROP_NAME, getName());
    return baseContext;
  }

  public String getName() {
    return getClass().getName();
  }

  /**
   * @return a map which defines the context where the actor is run. Per default it just returns a
   *     map with the actor name. Ideally sub classes add more context, like the partition id etc.
   */
  public Map<String, String> getContext() {
    if (context == null) {
      context = Collections.unmodifiableMap(createContext());
    }
    return context;
  }

  public boolean isActorClosed() {
    return actor.isClosed();
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

  public static Actor wrap(final Consumer<ActorControl> r) {
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

  @Override
  public void close() {
    closeAsync().join(MAX_CLOSE_TIMEOUT, TimeUnit.SECONDS);
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    return actor.close();
  }

  public static String buildActorName(final int nodeId, final String name) {
    return String.format("Broker-%d-%s", nodeId, name);
  }

  public static String buildActorName(final int nodeId, final String name, final int partitionId) {
    return String.format("Broker-%d-%s-%d", nodeId, name, partitionId);
  }

  /** Invoked when a task throws and the actor phase is not 'STARTING' and 'CLOSING'. */
  protected void handleFailure(final Throwable failure) {
    Loggers.ACTOR_LOGGER.error(
        "Uncaught exception in '{}' in phase '{}'. Continuing with next job.",
        getName(),
        actor.getLifecyclePhase(),
        failure);
  }

  public void onActorFailed() {
    // clean ups
  }

  @Override
  public <T> void runOnCompletion(
      final ActorFuture<T> future, final BiConsumer<T, Throwable> callback) {
    actor.runOnCompletion(future, callback);
  }

  @Override
  public void run(final Runnable action) {
    actor.run(action);
  }
}
