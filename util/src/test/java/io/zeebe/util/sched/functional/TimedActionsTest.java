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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ScheduledTimer;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;

public class TimedActionsTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldNotRunActionIfDeadlineNotReached() throws InterruptedException {
    // given
    final Runnable action = mock(Runnable.class);
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.runDelayed(Duration.ofMillis(10), action);
          }
        };

    // when
    schedulerRule.getClock().setCurrentTime(100);
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    verify(action, never()).run();
  }

  @Test
  public void shouldRunActionWhenDeadlineReached() throws InterruptedException {
    // given
    final Runnable action = mock(Runnable.class);
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.runDelayed(Duration.ofMillis(10), action);
          }
        };

    // when
    schedulerRule.getClock().setCurrentTime(100);
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

    // then
    verify(action, times(1)).run();
  }

  @Test
  public void shouldRunAtFixedRate() throws InterruptedException {
    // given
    final Runnable action = mock(Runnable.class);
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.runAtFixedRate(Duration.ofMillis(10), action);
          }
        };

    // when then
    schedulerRule.getClock().setCurrentTime(100);
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();
    verify(action, times(1)).run();

    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();
    verify(action, times(2)).run();

    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();
    verify(action, times(3)).run();
  }

  @Test
  public void shouldCancelRunDelayed() {
    // given
    final Runnable action = mock(Runnable.class);
    final AtomicReference<ScheduledTimer> timer = new AtomicReference<>();
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            timer.set(actor.runDelayed(Duration.ofMillis(10), action));
          }
        };

    // when
    schedulerRule.getClock().setCurrentTime(100);
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    timer.get().cancel();
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

    // then
    verify(action, times(0)).run();
  }

  @Test
  public void shouldCancelRunDelayedAfterExecution() {
    // given
    final Runnable action = mock(Runnable.class);
    final AtomicReference<ScheduledTimer> timer = new AtomicReference<>();
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            timer.set(actor.runDelayed(Duration.ofMillis(10), action));
          }
        };

    // when
    schedulerRule.getClock().setCurrentTime(100);
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // make timer run
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

    // when
    timer.get().cancel();

    // then
    // no exception has been thrown
  }

  @Test
  public void shouldCancelRunAtFixedRate() {
    // given
    final Runnable action = mock(Runnable.class);
    final AtomicReference<ScheduledTimer> timer = new AtomicReference<>();
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            timer.set(actor.runAtFixedRate(Duration.ofMillis(10), action));
          }
        };

    // when
    schedulerRule.getClock().setCurrentTime(100);
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    timer.get().cancel();
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

    // then
    verify(action, times(0)).run();
  }
}
