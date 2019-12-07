/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched.lifecycle;

import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.CLOSED;
import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.CLOSE_REQUESTED;
import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.CLOSING;
import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.STARTED;
import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.STARTING;
import static org.assertj.core.util.Lists.newArrayList;
import static org.mockito.Mockito.mock;

import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ActorTask.ActorLifecyclePhase;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

class LifecycleRecordingActor extends Actor {
  public static final List<ActorLifecyclePhase> FULL_LIFECYCLE =
      newArrayList(STARTING, STARTED, CLOSE_REQUESTED, CLOSING, CLOSED);

  public List<ActorLifecyclePhase> phases = new ArrayList<>();

  @Override
  public void onActorStarting() {
    phases.add(actor.getLifecyclePhase());
  }

  @Override
  public void onActorStarted() {
    phases.add(actor.getLifecyclePhase());
  }

  @Override
  public void onActorClosing() {
    phases.add(actor.getLifecyclePhase());
  }

  @Override
  public void onActorClosed() {
    phases.add(actor.getLifecyclePhase());
  }

  @Override
  public void onActorCloseRequested() {
    phases.add(actor.getLifecyclePhase());
  }

  protected void blockPhase() {
    blockPhase(new CompletableActorFuture<>(), mock(BiConsumer.class));
  }

  protected void blockPhase(ActorFuture<Void> future) {
    blockPhase(future, mock(BiConsumer.class));
  }

  @SuppressWarnings("unchecked")
  protected void blockPhase(ActorFuture<Void> future, BiConsumer consumer) {
    actor.runOnCompletionBlockingCurrentPhase(future, consumer);
  }

  @SuppressWarnings("unchecked")
  protected void runOnCompletion() {
    actor.runOnCompletion(new CompletableActorFuture<>(), mock(BiConsumer.class));
  }

  @SuppressWarnings("unchecked")
  protected void runOnCompletion(ActorFuture<Void> future, BiConsumer consumer) {
    actor.runOnCompletion(future, consumer);
  }

  @SuppressWarnings("unchecked")
  protected void runOnCompletion(ActorFuture<Void> future) {
    actor.runOnCompletion(future, mock(BiConsumer.class));
  }

  public ActorControl control() {
    return actor;
  }
}
