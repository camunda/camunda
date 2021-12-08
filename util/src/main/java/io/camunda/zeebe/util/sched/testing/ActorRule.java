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
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import org.junit.rules.ExternalResource;

public class ActorRule extends ExternalResource {

  private final ActorSchedulerRule actorSchedulerRule;
  private final String name;

  private ActorControl actorControl = null;
  private Actor actor = null;

  public ActorRule(final ActorSchedulerRule actorSchedulerRule) {
    this(actorSchedulerRule, null);
  }

  public ActorRule(final ActorSchedulerRule actorSchedulerRule, final String name) {
    this.actorSchedulerRule = actorSchedulerRule;
    this.name = name;
  }

  @Override
  public void before() throws Throwable {
    final var actorStartedHandlerFuture = new CompletableActorFuture<ActorControl>();

    actor =
        Actor.newActor()
            .name(name)
            .actorStartedHandler((t) -> actorStartedHandlerFuture.complete(t))
            .build();
    actorSchedulerRule.submitActor(actor);

    actorControl = actorStartedHandlerFuture.get();
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
