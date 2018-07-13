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
package io.zeebe.util.sched.testing;

import io.zeebe.util.LangUtil;
import io.zeebe.util.sched.ActorThread;
import io.zeebe.util.sched.ActorThreadGroup;
import io.zeebe.util.sched.ActorTimerQueue;
import io.zeebe.util.sched.TaskScheduler;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.metrics.ActorThreadMetrics;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class ControlledActorThread extends ActorThread {
  private CyclicBarrier barrier = new CyclicBarrier(2);

  public ControlledActorThread(
      String name,
      int id,
      ActorThreadGroup threadGroup,
      TaskScheduler taskScheduler,
      ActorClock clock,
      ActorThreadMetrics metrics,
      ActorTimerQueue timerQueue) {
    super(name, id, threadGroup, taskScheduler, clock, metrics, timerQueue);
    idleStrategy = new ControlledIdleStartegy();
  }

  class ControlledIdleStartegy extends ActorTaskRunnerIdleStrategy {
    @Override
    protected void onIdle() {
      super.onIdle();

      try {
        barrier.await();
      } catch (InterruptedException | BrokenBarrierException e) {
        LangUtil.rethrowUnchecked(e);
      }
    }
  }

  public void workUntilDone() {
    try {
      barrier.await(); // work at least 1 full cycle until the runner becomes idle after having been
      while (barrier.getNumberWaiting() < 1) {
        // spin until thread is idle again
        Thread.yield();
      }
    } catch (InterruptedException | BrokenBarrierException e) {
      LangUtil.rethrowUnchecked(e);
    }
  }
}
