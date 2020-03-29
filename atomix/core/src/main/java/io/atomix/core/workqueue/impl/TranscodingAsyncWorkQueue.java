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

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.core.workqueue.AsyncWorkQueue;
import io.atomix.core.workqueue.Task;
import io.atomix.core.workqueue.WorkQueue;
import io.atomix.core.workqueue.WorkQueueStats;
import io.atomix.primitive.impl.DelegatingAsyncPrimitive;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Transcoding async work queue. */
public class TranscodingAsyncWorkQueue<V1, V2> extends DelegatingAsyncPrimitive
    implements AsyncWorkQueue<V1> {

  private final AsyncWorkQueue<V2> backingQueue;
  private final Function<V1, V2> valueEncoder;
  private final Function<V2, V1> valueDecoder;

  public TranscodingAsyncWorkQueue(
      final AsyncWorkQueue<V2> backingQueue,
      final Function<V1, V2> valueEncoder,
      final Function<V2, V1> valueDecoder) {
    super(backingQueue);
    this.backingQueue = backingQueue;
    this.valueEncoder = valueEncoder;
    this.valueDecoder = valueDecoder;
  }

  @Override
  public CompletableFuture<Void> addMultiple(final Collection<V1> items) {
    return backingQueue.addMultiple(items.stream().map(valueEncoder).collect(Collectors.toList()));
  }

  @Override
  public CompletableFuture<Collection<Task<V1>>> take(final int maxItems) {
    return backingQueue
        .take(maxItems)
        .thenApply(
            tasks -> tasks.stream().map(t -> t.map(valueDecoder)).collect(Collectors.toList()));
  }

  @Override
  public CompletableFuture<Void> complete(final Collection<String> taskIds) {
    return backingQueue.complete(taskIds);
  }

  @Override
  public CompletableFuture<Void> registerTaskProcessor(
      final Consumer<V1> taskProcessor, final int parallelism, final Executor executor) {
    return backingQueue.registerTaskProcessor(
        v -> taskProcessor.accept(valueDecoder.apply(v)), parallelism, executor);
  }

  @Override
  public CompletableFuture<Void> stopProcessing() {
    return backingQueue.stopProcessing();
  }

  @Override
  public CompletableFuture<WorkQueueStats> stats() {
    return backingQueue.stats();
  }

  @Override
  public WorkQueue<V1> sync(final Duration operationTimeout) {
    return new BlockingWorkQueue<>(this, operationTimeout.toMillis());
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("backingValue", backingQueue).toString();
  }
}
