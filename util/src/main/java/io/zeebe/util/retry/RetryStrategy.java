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

import io.zeebe.util.sched.future.ActorFuture;
import java.util.function.BooleanSupplier;

public interface RetryStrategy {

  /**
   * Runs the given runnable with the defined retry strategy.
   *
   * <p>Returns an actor future, which will be completed when the callable was successfully executed
   * and has returned true.
   *
   * @param callable the callable which should be executed
   * @return a future, which is completed with true if the execution was successful
   */
  ActorFuture<Boolean> runWithRetry(OperationToRetry callable);

  /**
   * Runs the given runnable with the defined retry strategy.
   *
   * <p>Returns an actor future, which will be completed when the callable was successfully executed
   * and has returned true.
   *
   * @param callable the callable which should be executed
   * @param terminateCondition condition is called when callable returns false, if terminate
   *     condition returns true the retry strategy is aborted
   * @return a future, which is completed with true if the execution was successful
   */
  ActorFuture<Boolean> runWithRetry(OperationToRetry callable, BooleanSupplier terminateCondition);
}
