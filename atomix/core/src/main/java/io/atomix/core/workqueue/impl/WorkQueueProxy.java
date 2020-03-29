/*
 * Copyright 2016-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.core.workqueue.impl;

import static io.atomix.utils.concurrent.Threads.namedThreads;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.slf4j.LoggerFactory.getLogger;

import com.google.common.collect.ImmutableList;
import io.atomix.core.workqueue.AsyncWorkQueue;
import io.atomix.core.workqueue.Task;
import io.atomix.core.workqueue.WorkQueue;
import io.atomix.core.workqueue.WorkQueueStats;
import io.atomix.primitive.AbstractAsyncPrimitive;
import io.atomix.primitive.PrimitiveRegistry;
import io.atomix.primitive.PrimitiveState;
import io.atomix.primitive.proxy.ProxyClient;
import io.atomix.utils.concurrent.AbstractAccumulator;
import io.atomix.utils.concurrent.Accumulator;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;

/** Distributed resource providing the {@link WorkQueue} primitive. */
public class WorkQueueProxy extends AbstractAsyncPrimitive<AsyncWorkQueue<byte[]>, WorkQueueService>
    implements AsyncWorkQueue<byte[]>, WorkQueueClient {

  private final Logger log = getLogger(getClass());
  private final ExecutorService executor;
  private final AtomicReference<TaskProcessor> taskProcessor = new AtomicReference<>();
  private final Timer timer = new Timer("atomix-work-queue-completer");
  private final AtomicBoolean isRegistered = new AtomicBoolean(false);

  public WorkQueueProxy(
      final ProxyClient<WorkQueueService> proxy, final PrimitiveRegistry registry) {
    super(proxy, registry);
    executor =
        newSingleThreadExecutor(namedThreads("atomix-work-queue-" + proxy.name() + "-%d", log));
  }

  @Override
  public void taskAvailable() {
    resumeWork();
  }

  @Override
  public CompletableFuture<Void> addMultiple(final Collection<byte[]> items) {
    if (items.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    return getProxyClient().acceptBy(name(), service -> service.add(items));
  }

  @Override
  public CompletableFuture<Collection<Task<byte[]>>> take(final int maxTasks) {
    if (maxTasks <= 0) {
      return CompletableFuture.completedFuture(ImmutableList.of());
    }
    return getProxyClient().applyBy(name(), service -> service.take(maxTasks));
  }

  @Override
  public CompletableFuture<Void> complete(final Collection<String> taskIds) {
    if (taskIds.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    return getProxyClient().acceptBy(name(), service -> service.complete(taskIds));
  }

  @Override
  public CompletableFuture<Void> registerTaskProcessor(
      final Consumer<byte[]> callback, final int parallelism, final Executor executor) {
    final Accumulator<String> completedTaskAccumulator =
        new CompletedTaskAccumulator(timer, 50, 50); // TODO: make configurable
    taskProcessor.set(new TaskProcessor(callback, parallelism, executor, completedTaskAccumulator));
    return register().thenCompose(v -> take(parallelism)).thenAccept(taskProcessor.get());
  }

  @Override
  public CompletableFuture<Void> stopProcessing() {
    return unregister();
  }

  @Override
  public CompletableFuture<WorkQueueStats> stats() {
    return getProxyClient().applyBy(name(), service -> service.stats());
  }

  private void resumeWork() {
    final TaskProcessor activeProcessor = taskProcessor.get();
    if (activeProcessor == null) {
      return;
    }
    this.take(activeProcessor.headRoom())
        .whenCompleteAsync((tasks, e) -> activeProcessor.accept(tasks), executor);
  }

  private CompletableFuture<Void> register() {
    return getProxyClient()
        .acceptBy(name(), service -> service.register())
        .thenRun(() -> isRegistered.set(true));
  }

  private CompletableFuture<Void> unregister() {
    return getProxyClient()
        .acceptBy(name(), service -> service.unregister())
        .thenRun(() -> isRegistered.set(false));
  }

  @Override
  public CompletableFuture<AsyncWorkQueue<byte[]>> connect() {
    return super.connect()
        .thenCompose(v -> getProxyClient().getPartition(name()).connect())
        .thenRun(
            () ->
                getProxyClient()
                    .getPartition(name())
                    .addStateChangeListener(
                        state -> {
                          if (state == PrimitiveState.CONNECTED && isRegistered.get()) {
                            getProxyClient().acceptBy(name(), service -> service.register());
                          }
                        }))
        .thenApply(v -> this);
  }

  @Override
  public CompletableFuture<Void> delete() {
    executor.shutdown();
    timer.cancel();
    return super.delete();
  }

  @Override
  public WorkQueue<byte[]> sync(final Duration operationTimeout) {
    return new BlockingWorkQueue<>(this, operationTimeout.toMillis());
  }

  // TaskId accumulator for paced triggering of task completion calls.
  private class CompletedTaskAccumulator extends AbstractAccumulator<String> {
    CompletedTaskAccumulator(
        final Timer timer, final int maxTasksToBatch, final int maxBatchMillis) {
      super(timer, maxTasksToBatch, maxBatchMillis, Integer.MAX_VALUE);
    }

    @Override
    public void processItems(final List<String> items) {
      complete(items);
    }
  }

  private class TaskProcessor implements Consumer<Collection<Task<byte[]>>> {

    private final AtomicInteger headRoom;
    private final Consumer<byte[]> backingConsumer;
    private final Executor executor;
    private final Accumulator<String> taskCompleter;

    TaskProcessor(
        final Consumer<byte[]> backingConsumer,
        final int parallelism,
        final Executor executor,
        final Accumulator<String> taskCompleter) {
      this.backingConsumer = backingConsumer;
      this.headRoom = new AtomicInteger(parallelism);
      this.executor = executor;
      this.taskCompleter = taskCompleter;
    }

    int headRoom() {
      return headRoom.get();
    }

    @Override
    public void accept(final Collection<Task<byte[]>> tasks) {
      if (tasks == null) {
        return;
      }
      headRoom.addAndGet(-1 * tasks.size());
      tasks.forEach(
          task ->
              executor.execute(
                  () -> {
                    try {
                      backingConsumer.accept(task.payload());
                      taskCompleter.add(task.taskId());
                    } catch (final Exception e) {
                      log.debug("Task execution failed", e);
                    } finally {
                      headRoom.incrementAndGet();
                      resumeWork();
                    }
                  }));
    }
  }
}
