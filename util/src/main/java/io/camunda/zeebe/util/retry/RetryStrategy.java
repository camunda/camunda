/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
