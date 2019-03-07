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

import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.ActorScheduler.ActorSchedulerBuilder;
import io.zeebe.util.sched.ActorScheduler.ActorThreadFactory;
import io.zeebe.util.sched.ActorThread;
import io.zeebe.util.sched.ActorThreadGroup;
import io.zeebe.util.sched.ActorTimerQueue;
import io.zeebe.util.sched.TaskScheduler;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.metrics.ActorThreadMetrics;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.Assert;
import org.junit.rules.ExternalResource;

public class ControlledActorSchedulerRule extends ExternalResource {
  private final ActorScheduler actorScheduler;
  private final ControlledActorThread controlledActorTaskRunner;
  private final ThreadPoolExecutor blockingTasksRunner;
  private final ControlledActorClock clock = new ControlledActorClock();

  public ControlledActorSchedulerRule() {
    final ControlledActorThreadFactory actorTaskRunnerFactory = new ControlledActorThreadFactory();
    final ActorTimerQueue timerQueue = new ActorTimerQueue(clock, 1);
    final ActorSchedulerBuilder builder =
        ActorScheduler.newActorScheduler()
            .setActorClock(clock)
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(0)
            .setActorThreadFactory(actorTaskRunnerFactory)
            .setBlockingTasksShutdownTime(Duration.ofSeconds(0))
            .setActorTimerQueue(timerQueue);

    actorScheduler = builder.build();
    controlledActorTaskRunner = actorTaskRunnerFactory.controlledThread;
    blockingTasksRunner = builder.getBlockingTasksRunner();
  }

  @Override
  protected void before() {
    actorScheduler.start();
  }

  @Override
  protected void after() {
    actorScheduler.stop();
  }

  public ActorFuture<Void> submitActor(Actor actor) {
    return actorScheduler.submitActor(actor);
  }

  public ActorScheduler get() {
    return actorScheduler;
  }

  public void awaitBlockingTasksCompleted(int i) {
    final long currentTimeMillis = System.currentTimeMillis();

    while (System.currentTimeMillis() - currentTimeMillis < 5000) {
      final long completedTaskCount = blockingTasksRunner.getCompletedTaskCount();
      if (completedTaskCount >= i) {
        return;
      }
    }

    Assert.fail("could not complete " + i + " blocking tasks within 5s");
  }

  public void workUntilDone() {
    controlledActorTaskRunner.workUntilDone();
  }

  public <T> ActorFuture<T> call(Callable<T> callable) {
    final ActorFuture<T> future = new CompletableActorFuture<>();
    submitActor(new CallingActor(future, callable));
    return future;
  }

  static class CallingActor<T> extends Actor {
    private final ActorFuture<T> future;
    private final Callable<T> callable;

    CallingActor(ActorFuture<T> future, Callable<T> callable) {
      this.future = future;
      this.callable = callable;
    }

    @Override
    protected void onActorStarted() {
      actor.run(
          () -> {
            try {
              future.complete(callable.call());
            } catch (Exception e) {
              future.completeExceptionally(e);
            }
          });
    }
  }

  static class ControlledActorThreadFactory implements ActorThreadFactory {
    private ControlledActorThread controlledThread;

    @Override
    public ActorThread newThread(
        String name,
        int id,
        ActorThreadGroup threadGroup,
        TaskScheduler taskScheduler,
        ActorClock clock,
        ActorThreadMetrics metrics,
        ActorTimerQueue timerQueue) {
      controlledThread =
          new ControlledActorThread(
              name, id, threadGroup, taskScheduler, clock, metrics, timerQueue);
      return controlledThread;
    }
  }

  public ControlledActorClock getClock() {
    return clock;
  }
}
