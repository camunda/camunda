/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Loggers;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public abstract class Actor implements AutoCloseable, AsyncClosable, ConcurrencyControl {

  public static final String ACTOR_PROP_NAME = "actor-name";
  public static final String ACTOR_PROP_PARTITION_ID = "partitionId";

  /**
   * MDC key for the partition group (a.k.a. physical tenant) the actor belongs to. Mirrors the
   * {@code physicalTenant} common metrics label (see {@code PartitionKeyNames}).
   */
  public static final String ACTOR_PROP_PHYSICAL_TENANT = "physicalTenant";

  private static final int MAX_CLOSE_TIMEOUT = 300;
  protected final ActorControl actor = new ActorControl(this);
  private final Map<String, String> context;
  private final String name;

  /**
   * Creates a new actor with name and context derived from the given parameters.
   *
   * @param name A name for the actor. If {@code null}, the name of the class will be used.
   * @param partitionId The partition the actor belongs to. Unless this is {@code null}, the actor
   *     name will end with the partition number and the context will contain the partition group
   *     and number.
   * @param additionalContext Any additional context to be included in the actor's context besides
   *     the automatically added {@link #ACTOR_PROP_NAME}, {@link #ACTOR_PROP_PARTITION_ID} and
   *     {@link #ACTOR_PROP_PHYSICAL_TENANT}.
   */
  protected Actor(
      @Nullable final String name,
      @Nullable final PartitionId partitionId,
      @Nullable final Map<String, String> additionalContext) {
    final var actorName = Objects.requireNonNullElse(name, getClass().getSimpleName());
    this.name = partitionId != null ? buildActorName(actorName, partitionId.number()) : actorName;
    context = buildContext(this.name, partitionId, additionalContext);
  }

  /** Creates an actor, named by its class, without a partition or additional context. */
  protected Actor() {
    this(null, null, null);
  }

  /**
   * Creates an actor with the provided name, without a partition or additional context.
   *
   * @param name A name for the actor. If {@code null}, the name of the class will be used.
   */
  protected Actor(final String name) {
    this(name, null, null);
  }

  /**
   * Creates a new actor with name and context derived from the given parameters.
   *
   * @param name A name for the actor. If {@code null}, the name of the class will be used.
   * @param partitionId The partition the actor belongs to. Unless this is {@code null}, the actor
   *     name will end with the partition number and the context will contain the partition group
   *     and number.
   */
  protected Actor(final String name, final PartitionId partitionId) {
    this(name, partitionId, null);
  }

  private static Map<String, String> buildContext(
      final String name,
      final PartitionId partitionId,
      final Map<String, String> additionalContext) {
    final var context =
        new HashMap<String, String>(
            3); // most actors have a name and associated partition but no additional context
    context.put(ACTOR_PROP_NAME, name);
    if (partitionId != null) {
      context.put(ACTOR_PROP_PARTITION_ID, Integer.toString(partitionId.number()));
      context.put(ACTOR_PROP_PHYSICAL_TENANT, partitionId.group());
    }
    if (additionalContext != null) {
      context.putAll(additionalContext);
    }
    return context;
  }

  public final String getName() {
    return name;
  }

  /**
   * @return a map that defines the context where the actor is run, as provided to the constructor.
   */
  public Map<String, String> getContext() {
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
    return new Actor(r.toString()) {

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
      final ActorFuture<T> future, final BiConsumer<T, @Nullable Throwable> callback) {
    actor.runOnCompletion(future, callback);
  }

  @Override
  public <T> void runOnCompletion(
      final Collection<ActorFuture<T>> actorFutures, final Consumer<@Nullable Throwable> callback) {
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
  public ScheduledTimer schedule(final long delayMs, final Runnable runnable) {
    return actor.schedule(delayMs, runnable);
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
