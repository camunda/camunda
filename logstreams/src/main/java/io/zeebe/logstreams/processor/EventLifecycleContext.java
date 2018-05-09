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
package io.zeebe.logstreams.processor;

import io.zeebe.util.sched.future.ActorFuture;

public interface EventLifecycleContext
{
    /**
     * <strong>Use with care</strong>
     * <p>
     * If you need to perform an async action (like fetching some data) while processing the event, you
     * can supply a future here. Note that:
     * <ul>
     * <li>the async action should not be a side effect (in that case use {@link #executeSideEffects()}).</li>
     * <li>if the outcome of the action influences the outcome of processing the event (ie. based on the return
     * value of the async action either a command succeeds or is rejected, make sure that reprocessing produces the
     * same results. There are two cases:</li>
     * <ul>
     * <li>In case the async action always returns the same data no extra care is necessary</li>
     * <li>In case the async action does not always return the same data, make sure to a) send responses to a client
     * based on the event that is written to the stream, b) do not update state on the command but rather on the event.</li>
     * </ul>
     * </ul>
     *
     * Also note that the following pattern does not work
     * <pre>
     * void processEvent(EventLifecycleContext ctx)
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
     * </pre>
     * The problem is that now both the stream processor and the actor.runOnCompletion are waiting
     * on the same future and there is no guaranteed order in which these are invoked. So it could
     * happen that the stream processor moves to the next processing stage before the callback runs.
     *
     * You can refactor it to this:
     * <pre>
     * void processEvent(EventLifecycleContext ctx)
     * {
     *      ActorFuture<SomeData> future = asyncApi.fetchData();
     *
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
     *
     * @param future the future to pass
     */
    void async(ActorFuture<?> future);
}
