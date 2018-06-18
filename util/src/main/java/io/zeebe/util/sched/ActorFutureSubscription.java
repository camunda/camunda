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
package io.zeebe.util.sched;

import io.zeebe.util.sched.ActorTask.ActorLifecyclePhase;
import io.zeebe.util.sched.future.ActorFuture;

public class ActorFutureSubscription implements ActorSubscription {
  private final ActorJob callbackJob;
  private ActorFuture<?> future;
  private final int phaseMask;

  public ActorFutureSubscription(ActorFuture<?> future, ActorJob callbackJob, int phaseMask) {
    this.future = future;
    this.callbackJob = callbackJob;
    this.phaseMask = phaseMask;
  }

  @Override
  public boolean triggersInPhase(ActorLifecyclePhase phase) {
    // triggers in all phases
    return phase != ActorLifecyclePhase.CLOSED && (phase.getValue() & phaseMask) > 0;
  }

  @Override
  public boolean poll() {
    return future.isDone();
  }

  @Override
  public ActorJob getJob() {
    return callbackJob;
  }

  @Override
  public boolean isRecurring() {
    return false;
  }
}
