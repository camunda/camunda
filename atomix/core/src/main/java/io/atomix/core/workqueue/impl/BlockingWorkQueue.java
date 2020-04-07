/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.core.workqueue.impl;

import com.google.common.base.Throwables;
import io.atomix.core.workqueue.AsyncWorkQueue;
import io.atomix.core.workqueue.Task;
import io.atomix.core.workqueue.WorkQueue;
import io.atomix.core.workqueue.WorkQueueStats;
import io.atomix.primitive.PrimitiveException;
import io.atomix.primitive.Synchronous;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/** Default synchronous work queue implementation. */
public class BlockingWorkQueue<E> extends Synchronous<AsyncWorkQueue<E>> implements WorkQueue<E> {

  private final AsyncWorkQueue<E> asyncQueue;
  private final long operationTimeoutMillis;

  public BlockingWorkQueue(final AsyncWorkQueue<E> asyncQueue, final long operationTimeoutMillis) {
    super(asyncQueue);
    this.asyncQueue = asyncQueue;
    this.operationTimeoutMillis = operationTimeoutMillis;
  }

  @Override
  public void addMultiple(final Collection<E> items) {
    complete(asyncQueue.addMultiple(items));
  }

  @Override
  public Collection<Task<E>> take(final int maxItems) {
    return complete(asyncQueue.take(maxItems));
  }

  @Override
  public void complete(final Collection<String> taskIds) {
    complete(asyncQueue.complete(taskIds));
  }

  @Override
  public void registerTaskProcessor(
      final Consumer<E> taskProcessor, final int parallelism, final Executor executor) {
    complete(asyncQueue.registerTaskProcessor(taskProcessor, parallelism, executor));
  }

  @Override
  public void stopProcessing() {
    complete(asyncQueue.stopProcessing());
  }

  @Override
  public WorkQueueStats stats() {
    return complete(asyncQueue.stats());
  }

  @Override
  public AsyncWorkQueue<E> async() {
    return asyncQueue;
  }

  private <T> T complete(final CompletableFuture<T> future) {
    try {
      return future.get(operationTimeoutMillis, TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PrimitiveException.Interrupted();
    } catch (final TimeoutException e) {
      throw new PrimitiveException.Timeout();
    } catch (final ExecutionException e) {
      final Throwable cause = Throwables.getRootCause(e);
      if (cause instanceof PrimitiveException) {
        throw (PrimitiveException) cause;
      } else {
        throw new PrimitiveException(cause);
      }
    }
  }
}
