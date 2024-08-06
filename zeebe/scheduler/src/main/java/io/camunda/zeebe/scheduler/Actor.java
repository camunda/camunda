/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Loggers;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class Actor implements AutoCloseable, AsyncClosable, ConcurrencyControl {

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
    return getClass().getSimpleName();
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

  public static String buildActorName(final String name, final int partitionId) {
    return "%s-%d".formatted(name, partitionId);
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
  public <T> void runOnCompletion(
      final Collection<ActorFuture<T>> actorFutures, final Consumer<Throwable> callback) {
    actor.runOnCompletion(actorFutures, callback);
  }

  @Override
  public void run(final Runnable action) {
    actor.run(action);
  }

  @Override
  public <T> ActorFuture<T> call(final Callable<T> callable) {
    return actor.call(callable);
  }

  @Override
  public ScheduledTimer schedule(final Duration delay, final Runnable runnable) {
    return actor.schedule(delay, runnable);
  }

  public static ActorBuilder newActor() {
    return new ActorBuilder();
  }

  public static class ActorBuilder {

    private String name;
    private Consumer<ActorControl> actorStartedHandler;

    public ActorBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public ActorBuilder actorStartedHandler(final Consumer<ActorControl> actorStartedHandler) {
      this.actorStartedHandler = actorStartedHandler;
      return this;
    }

    public Actor build() {
      final var wrapper =
          new Consumer<ActorControl>() {

            @Override
            public String toString() {
              if (name != null) {
                return name;
              } else if (actorStartedHandler != null) {
                return actorStartedHandler.getClass().getName();
              } else {
                return super.toString();
              }
            }

            @Override
            public void accept(final ActorControl t) {
              if (actorStartedHandler != null) {
                actorStartedHandler.accept(t);
              }
            }
          };

      return wrap(wrapper);
    }
  }
}
