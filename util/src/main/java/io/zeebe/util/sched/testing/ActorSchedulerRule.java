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
import io.zeebe.util.sched.FutureUtil;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import org.junit.rules.ExternalResource;

public class ActorSchedulerRule extends ExternalResource {
  private final ActorScheduler actorScheduler;
  private ActorSchedulerBuilder builder;

  public ActorSchedulerRule(int numOfThreads, ActorClock clock) {
    this(numOfThreads, 2, clock);
  }

  public ActorSchedulerRule(int numOfThreads, int numOfIoThreads, ActorClock clock) {
    builder =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(numOfThreads)
            .setIoBoundActorThreadCount(numOfIoThreads)
            .setActorClock(clock);

    actorScheduler = builder.build();
  }

  public ActorSchedulerRule(int numOfThreads) {
    this(numOfThreads, null);
  }

  public ActorSchedulerRule(ActorClock clock) {
    this(Math.max(1, Runtime.getRuntime().availableProcessors() - 2), clock);
  }

  public ActorSchedulerRule() {
    this(null);
  }

  @Override
  protected void before() {
    actorScheduler.start();
  }

  @Override
  protected void after() {
    FutureUtil.join(actorScheduler.stop());
  }

  public ActorFuture<Void> submitActor(Actor actor) {
    return actorScheduler.submitActor(actor);
  }

  public ActorFuture<Void> submitActor(Actor actor, boolean useCountersManager) {
    return actorScheduler.submitActor(actor, useCountersManager);
  }

  public ActorScheduler get() {
    return actorScheduler;
  }

  public ActorSchedulerBuilder getBuilder() {
    return builder;
  }
}
