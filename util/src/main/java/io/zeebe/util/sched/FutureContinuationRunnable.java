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

import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

import io.zeebe.util.sched.future.ActorFuture;

public class FutureContinuationRunnable<T> implements Runnable
{
    final ActorTask task;
    final ActorFuture<T> future;
    final BiConsumer<T, Throwable> callback;
    final boolean ensureBlockedOnFuture;

    FutureContinuationRunnable(ActorTask task, ActorFuture<T> future, BiConsumer<T, Throwable> callback, boolean ensureBlockedOnFuture)
    {
        this.task = task;
        this.future = future;
        this.callback = callback;
        this.ensureBlockedOnFuture = ensureBlockedOnFuture;
    }

    @Override
    public void run()
    {
        try
        {
            if (!ensureBlockedOnFuture || task.awaitFuture == future)
            {
                final T res = future.get();
                callback.accept(res, null);
            }
            else
            {
                System.out.println("Not calling continuation future");
            }
        }
        catch (ExecutionException e)
        {
            callback.accept(null, e);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
}