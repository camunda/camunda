/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

  public ActorFuture<Void> close() {
    return actor.close();
  }

  protected void blockPhase() {
    blockPhase(new CompletableActorFuture<>(), mock(BiConsumer.class));
  }

  protected void blockPhase(BiConsumer consumer) {
    blockPhase(new CompletableActorFuture<>(), consumer);
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
  protected void runOnCompletion(BiConsumer consumer) {
    actor.runOnCompletion(new CompletableActorFuture<>(), consumer);
  }

  @SuppressWarnings("unchecked")
  protected void runOnCompletion(ActorFuture<Void> future) {
    actor.runOnCompletion(future, mock(BiConsumer.class));
  }

  public ActorControl control() {
    return actor;
  }
}
