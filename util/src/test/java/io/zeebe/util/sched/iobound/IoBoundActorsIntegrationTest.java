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
package io.zeebe.util.sched.iobound;

import static io.zeebe.util.sched.SchedulingHints.ioBound;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorThread;
import io.zeebe.util.sched.ActorThreadGroup;
import io.zeebe.util.sched.CpuThreadGroup;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;

public class IoBoundActorsIntegrationTest {
  @Rule public ActorSchedulerRule schedulerRule = new ActorSchedulerRule();

  @Test
  public void shouldRunIoBoundActor() {
    final ActorThreadGroup ioBoundActorThreads =
        schedulerRule.getBuilder().getIoBoundActorThreads();

    // given
    final AtomicReference<ActorThreadGroup> threadGroupRef =
        new AtomicReference<ActorThreadGroup>();
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarting() {
            threadGroupRef.set(ActorThread.current().getActorThreadGroup());
          }
        };

    // when
    schedulerRule.get().submitActor(actor, false, ioBound()).join();

    // then
    assertThat(threadGroupRef.get()).isEqualTo(ioBoundActorThreads);
  }

  @Test
  public void shouldStayOnIoBoundThreadGroupWhenInteractingWithCpuBound() {
    final ActorThreadGroup ioBoundActorThreads =
        schedulerRule.getBuilder().getIoBoundActorThreads();

    // given
    final AtomicBoolean isOnWrongThreadGroup = new AtomicBoolean();
    final CallableActor callableActor = new CallableActor(isOnWrongThreadGroup);
    final Actor ioBoundActor =
        new Actor() {
          @Override
          protected void onActorStarting() {
            for (int i = 0; i < 1_000; i++) {
              actor.runOnCompletion(callableActor.doCall(), this::callback);
            }
          }

          protected void callback(Void res, Throwable t) {
            if (ActorThread.current().getActorThreadGroup() != ioBoundActorThreads) {
              isOnWrongThreadGroup.set(true);
            }
          }
        };

    // when
    schedulerRule.submitActor(callableActor).join();
    schedulerRule.get().submitActor(ioBoundActor, false, ioBound()).join();

    // then
    assertThat(isOnWrongThreadGroup).isFalse();
  }

  @Test
  public void shouldStayOnIoBoundThreadGroupWhenInteractingWithCpuBoundOnBlockingPhase() {
    final ActorThreadGroup ioBoundActorThreads =
        schedulerRule.getBuilder().getIoBoundActorThreads();

    // given
    final AtomicBoolean isOnWrongThreadGroup = new AtomicBoolean();
    final CallableActor callableActor = new CallableActor(isOnWrongThreadGroup);
    final Actor ioBoundActor =
        new Actor() {
          @Override
          protected void onActorStarting() {
            for (int i = 0; i < 1_000; i++) {
              actor.runOnCompletionBlockingCurrentPhase(callableActor.doCall(), this::callback);
            }
          }

          protected void callback(Void res, Throwable t) {
            if (ActorThread.current().getActorThreadGroup() != ioBoundActorThreads) {
              isOnWrongThreadGroup.set(true);
            }
          }
        };

    // when
    schedulerRule.submitActor(callableActor).join();
    schedulerRule.get().submitActor(ioBoundActor, false, ioBound()).join();

    // then
    assertThat(isOnWrongThreadGroup).isFalse();
  }

  class CallableActor extends Actor {
    private AtomicBoolean isOnWrongThreadGroup;

    CallableActor(AtomicBoolean isOnWrongThreadGroup) {
      this.isOnWrongThreadGroup = isOnWrongThreadGroup;
    }

    public ActorFuture<Void> doCall() {
      return actor.call(
          () -> {
            if (!(ActorThread.current().getActorThreadGroup() instanceof CpuThreadGroup)) {
              isOnWrongThreadGroup.set(true);
            }
          });
    }
  }
}
