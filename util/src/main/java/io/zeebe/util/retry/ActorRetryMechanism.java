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
package io.zeebe.util.retry;

import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.function.BooleanSupplier;

public class ActorRetryMechanism {

  private final ActorControl actor;

  private OperationToRetry currentCallable;
  private BooleanSupplier currentTerminateCondition;
  private ActorFuture<Boolean> currentFuture;

  public ActorRetryMechanism(ActorControl actor) {
    this.actor = actor;
  }

  void wrap(
      OperationToRetry callable, BooleanSupplier condition, ActorFuture<Boolean> resultFuture) {
    currentCallable = callable;
    currentTerminateCondition = condition;
    currentFuture = resultFuture;
  }

  void run() throws Exception {
    if (currentCallable.run()) {
      currentFuture.complete(true);
      actor.done();
    } else if (currentTerminateCondition.getAsBoolean()) {
      currentFuture.complete(false);
      actor.done();
    } else {
      actor.yield();
    }
  }
}
