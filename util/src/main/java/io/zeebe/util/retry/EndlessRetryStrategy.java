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
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.function.BooleanSupplier;

public class EndlessRetryStrategy implements RetryStrategy {

  private final ActorControl actor;
  private final ActorRetryMechanism retryMechanism;
  private CompletableActorFuture<Boolean> currentFuture;
  private BooleanSupplier terminateCondition;

  public EndlessRetryStrategy(ActorControl actor) {
    this.actor = actor;
    this.retryMechanism = new ActorRetryMechanism(actor);
  }

  @Override
  public ActorFuture<Boolean> runWithRetry(OperationToRetry callable) {
    return runWithRetry(callable, () -> false);
  }

  @Override
  public ActorFuture<Boolean> runWithRetry(OperationToRetry callable, BooleanSupplier condition) {
    currentFuture = new CompletableActorFuture<>();
    terminateCondition = condition;
    retryMechanism.wrap(callable, terminateCondition, currentFuture);

    actor.runUntilDone(this::run);

    return currentFuture;
  }

  private void run() {
    try {
      retryMechanism.run();
    } catch (Exception exception) {
      if (terminateCondition.getAsBoolean()) {
        currentFuture.complete(false);
        actor.done();
      } else {
        actor.yield();
      }
    }
  }
}
