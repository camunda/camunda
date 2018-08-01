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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.util.TestUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import org.junit.Rule;
import org.junit.Test;

public class CallableExecutionTest {
  @Rule public final ActorSchedulerRule schedulerRule = new ActorSchedulerRule(3);

  @Test
  public void shouldCompleteFutureExceptionallyWhenSubmittedDuringActorClosedJob()
      throws InterruptedException, BrokenBarrierException {
    // given
    final CyclicBarrier barrier = new CyclicBarrier(2);
    final CloseableActor actor =
        new CloseableActor() {
          @Override
          protected void onActorClosed() {
            try {
              barrier.await(); // signal arrival at barrier
              barrier.await(); // wait for continuation
            } catch (InterruptedException | BrokenBarrierException e) {
              throw new RuntimeException(e);
            }
          }
        };

    schedulerRule.submitActor(actor);
    actor.close();
    barrier.await(); // wait for actor to reach onActorClosed callback

    final ActorFuture<Void> future = actor.doCall();

    // when
    barrier.await(); // signal actor to continue

    // then
    TestUtil.waitUntil(() -> future.isDone());
    assertThat(future).isDone();
    assertThatThrownBy(() -> future.get())
        .isInstanceOf(ExecutionException.class)
        .hasMessage("Actor is closed");
  }

  class CloseableActor extends Actor {
    ActorFuture<Void> doCall() {
      return actor.call(() -> {});
    }

    void close() {
      actor.close();
    }
  }
}
