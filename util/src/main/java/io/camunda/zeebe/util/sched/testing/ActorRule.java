/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched.testing;

import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorControl;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.rules.ExternalResource;

public class ActorRule extends ExternalResource {

  private final ActorSchedulerRule actorSchedulerRule;
  private final Function<CompletableFuture<ActorControl>, Actor> actorSupplier;
  private final String name;

  private ActorControl actorControl = null;
  private Actor actor = null;

  public ActorRule(final ActorSchedulerRule actorSchedulerRule) {
    this(actorSchedulerRule, null);
  }

  public ActorRule(final ActorSchedulerRule actorSchedulerRule, final String name) {
    this(actorSchedulerRule, null, name);
  }

  public ActorRule(
      final ActorSchedulerRule actorSchedulerRule,
      Function<CompletableFuture<ActorControl>, Actor> actorSupplier,
      final String name) {
    this.actorSchedulerRule = actorSchedulerRule;
    this.actorSupplier = actorSupplier != null ? actorSupplier : this::supplyDefaultActor;
    this.name = name;
  }

  private Actor supplyDefaultActor(CompletableFuture<ActorControl> future) {
    return Actor.newActor().name(name).onActorStartedHandler((t) -> future.complete(t)).build();
  }

  @Override
  public void before() throws Throwable {
    final var onActorStartedFuture = new CompletableFuture<ActorControl>();
    actor = actorSupplier.apply(onActorStartedFuture);
    actorSchedulerRule.submitActor(actor);
    actorControl = onActorStartedFuture.get();
  }

  @Override
  public void after() {
    actor.close();
  }

  public Actor getActor() {
    return actor;
  }

  public ActorControl getActorControl() {
    return actorControl;
  }
}
