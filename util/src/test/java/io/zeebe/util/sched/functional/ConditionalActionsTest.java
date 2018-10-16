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
package io.zeebe.util.sched.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;

public class ConditionalActionsTest {
  @Rule public ControlledActorSchedulerRule scheduler = new ControlledActorSchedulerRule();

  @Test
  public void shouldNotTriggerActionIfConditionNotTriggered() {
    // given
    final Runnable action = mock(Runnable.class);
    final AtomicReference<ActorCondition> condition = new AtomicReference<ActorCondition>(null);
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            condition.set(actor.onCondition("test", action));
          }
        };

    // when
    scheduler.submitActor(actor);
    scheduler.workUntilDone();

    // then
    verify(action, never()).run();
  }

  @Test
  public void shouldTriggerActionIfConditionTriggered() {
    // given
    final Runnable action = mock(Runnable.class);
    final AtomicReference<ActorCondition> condition = new AtomicReference<ActorCondition>(null);
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            condition.set(actor.onCondition("test", action));
          }
        };

    // when
    scheduler.submitActor(actor);
    scheduler.workUntilDone();
    condition.get().signal();
    scheduler.workUntilDone();

    // then
    verify(action, times(1)).run();
  }

  @Test
  public void shouldTriggerActionOnMultipleSubsequentTriggers() {
    // given
    final Runnable action = mock(Runnable.class);
    final AtomicReference<ActorCondition> condition = new AtomicReference<ActorCondition>(null);
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            condition.set(actor.onCondition("test", action));
          }
        };

    // when then
    scheduler.submitActor(actor);
    scheduler.workUntilDone();
    final ActorCondition actorCondition = condition.get();

    actorCondition.signal();
    scheduler.workUntilDone();
    verify(action, times(1)).run();

    actorCondition.signal();
    scheduler.workUntilDone();
    verify(action, times(2)).run();

    actorCondition.signal();
    scheduler.workUntilDone();
    verify(action, times(3)).run();

    actorCondition.signal();
    scheduler.workUntilDone();
    verify(action, times(4)).run();
  }

  @Test
  public void shouldTriggerActionOnMultipleSubsequentTriggersConcurrently() {
    // given
    final Runnable action = mock(Runnable.class);
    final AtomicReference<ActorCondition> condition = new AtomicReference<ActorCondition>(null);
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            condition.set(actor.onCondition("test", action));
          }
        };

    // when then
    scheduler.submitActor(actor);
    scheduler.workUntilDone();
    final ActorCondition actorCondition = condition.get();

    actorCondition.signal();
    actorCondition.signal();
    actorCondition.signal();
    actorCondition.signal();

    scheduler.workUntilDone();
    verify(action, times(4)).run();
  }

  @Test
  public void shouldTerminateOnFollowUpAndYield() {
    // given
    final AtomicInteger invocations = new AtomicInteger(0);
    final AtomicReference<ActorCondition> condition = new AtomicReference<>();

    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            condition.set(actor.onCondition("foo", this::onCondition));
          }

          protected void onCondition() {
            invocations.incrementAndGet();
            actor.run(this::doNothing);
            actor.yield();
          }

          protected void doNothing() {}
        };

    scheduler.submitActor(actor);
    scheduler.workUntilDone();

    // when
    condition.get().signal();
    scheduler.workUntilDone();

    // then
    assertThat(invocations.get()).isEqualTo(1);
  }
}
