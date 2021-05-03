/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched;

import io.zeebe.util.sched.future.ActorFuture;

public interface AsyncContext {
  /**
   * <strong>Use with care</strong>
   *
   * <p>Note that the following pattern does not work
   *
   * <pre>
   * void fetch(AsyncContext ctx)
   * {
   *      ActorFuture<SomeData> future = asyncApi.fetchData();
   *
   *      actor.runOnCompletion(future, (someData, err) ->
   *      {
   *          // do something
   *      });
   *
   *      ctx.async(future);
   * }
   *
   * </pre>
   *
   * The problem is that now, if another component is awaiting on future, both this component and
   * the actor.runOnCompletion are waiting on the same future, and there is no guaranteed order in
   * which these are invoked, which could lead to unexpected behaviour (e.g. the stream processor
   * moves to the next processing stage before the callback runs).
   *
   * <p>You can refactor it to this:
   *
   * <pre>
   * void process(AsyncContext ctx)
   * {
   *      ActorFuture<SomeData> future = asyncApi.fetchData();
   *      ActorFuture<Void> whenProcessingDone = new CompletableActorFuture();
   *
   *      actor.runOnCompletion(future, (someData, err) ->
   *      {
   *          // do something
   *          whenProcessingDone.complete(null);
   *      });
   *
   *      ctx.async(whenProcessingDone);
   * }
   * </pre>
   *
   * @param future the future to pass
   */
  void async(ActorFuture<?> future);
}
