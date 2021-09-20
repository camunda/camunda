/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.interceptors;

import io.camunda.zeebe.gateway.query.QueryApi;
import io.grpc.Context;
import io.grpc.Context.Key;

/** A set of utilities which interceptor authors can use in their interceptors. */
public final class InterceptorUtil {
  private static final Key<QueryApi> QUERY_API_KEY = Context.key("zeebe-query-api");

  private InterceptorUtil() {}

  /**
   * Returns an instance of {@link QueryApi} usable in an interceptor. Note that, as per the gRPC
   * documentation, it's perfectly fine to block in a call and/or listener, which may greatly
   * simplify the usage of the API in your code.
   *
   * <p>If you use the API asynchronously, there are a few gotchas to remember:
   *
   * <ul>
   *   <li>if your interceptor is loaded via an external JAR, and it uses directly or indirectly the
   *       {@link Thread#getContextClassLoader()} to load classes, you will need to make sure to set
   *       the appropriate context class loader in your callbacks, otherwise you may run into {@link
   *       ClassNotFoundException} errors
   *   <li>your callback may be executed on a different thread than the initial call, so you will
   *       have to deal with thread safety; using a {@link io.grpc.internal.SerializingExecutor} or
   *       similar may help
   *   <li>since your callback may be executed on a different thread, the {@link Context#current()}
   *       maybe different; if you want to use the same original context, you will need to close on
   *       it in your callback, or extract what you need from it beforehand and close on that
   * </ul>
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * final Context context = Context.current();
   * final QueryApi api = InterceptorUtil.getQueryApiKey().get(context);
   * final String processId;
   *
   * try {
   *  processId = queryApi.getBpmnProcessIdForProcess(processKey).toCompletableFuture().join();
   * } catch(final Exception e) {
   *   // close the call on error
   *   return;
   * }
   *
   * // do something with the processId
   * }</pre>
   *
   * @return the context key associated with the current query API
   */
  public static Key<QueryApi> getQueryApiKey() {
    return QUERY_API_KEY;
  }
}
