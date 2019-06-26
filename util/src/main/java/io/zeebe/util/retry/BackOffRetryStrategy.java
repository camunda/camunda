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
import java.time.Duration;
import java.util.function.BooleanSupplier;

public class BackOffRetryStrategy implements RetryStrategy {

  private final ActorControl actor;
  private final Duration maxBackOff;

  private Duration backOffDuration;
  private CompletableActorFuture<Boolean> currentFuture;
  private BooleanSupplier currentTerminateCondition;
  private OperationToRetry currentCallable;

  public BackOffRetryStrategy(ActorControl actor, Duration maxBackOff) {
    this.actor = actor;
    this.maxBackOff = maxBackOff;
  }

  @Override
  public ActorFuture<Boolean> runWithRetry(OperationToRetry callable) {
    return runWithRetry(callable, () -> false);
  }

  @Override
  public ActorFuture<Boolean> runWithRetry(
      OperationToRetry callable, BooleanSupplier terminateCondition) {
    currentFuture = new CompletableActorFuture<>();
    this.currentTerminateCondition = terminateCondition;
    currentCallable = callable;
    backOffDuration = Duration.ofSeconds(1);

    actor.run(this::run);

    return currentFuture;
  }

  private void run() {
    try {
      if (currentCallable.run()) {
        currentFuture.complete(true);
      } else if (currentTerminateCondition.getAsBoolean()) {
        currentFuture.complete(false);
      } else {
        backOff();
      }
    } catch (Exception exception) {
      if (currentTerminateCondition.getAsBoolean()) {
        currentFuture.complete(false);
      } else {
        backOff();
      }
    }
  }

  private void backOff() {
    final boolean notReachedMaxBackOff = !backOffDuration.equals(maxBackOff);
    if (notReachedMaxBackOff) {
      final Duration nextBackOff = backOffDuration.multipliedBy(2);
      backOffDuration = nextBackOff.compareTo(maxBackOff) < 0 ? nextBackOff : maxBackOff;
    }
    actor.runDelayed(backOffDuration, this::run);
  }
}
