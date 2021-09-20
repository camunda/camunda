/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.interceptors;

import io.grpc.Context;
import io.grpc.Context.Key;
import java.util.concurrent.Executor;

/** A set of utilities which interceptor authors can use in their interceptors. */
public final class InterceptorUtil {
  private static final Key<Executor> EXECUTOR_KEY = Context.key("zeebe-interceptor-executor");

  private InterceptorUtil() {}

  /**
   * @return the context key associated with the executor returned by {@link #getContextExecutor()}
   * @see #getContextExecutor()
   */
  public static Key<Executor> getExecutorKey() {
    return EXECUTOR_KEY;
  }

  /**
   * Returns an {@link Executor} which can be used in your interceptor when dealing with
   * asynchronous code. This executor will execute callbacks in the thread that is completing * the
   * future, but will ensure that the thread context class loader is correctly set before * calling
   * your callback.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * final CompletionStage<String> future = queryApi.getBpmnProcessIdForProcess(processKey);
   * future
   *     .whenCompleteAsync(
   *        (processId, error) -> this::onProcessId,
   *        InterceptorUtil.getContextExecutor());
   * }</pre>
   *
   * @return an executor which can be used when dealing with asynchronous code
   */
  public static Executor getContextExecutor() {
    return getExecutorKey().get();
  }
}
