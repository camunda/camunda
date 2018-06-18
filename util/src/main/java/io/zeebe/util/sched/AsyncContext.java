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
