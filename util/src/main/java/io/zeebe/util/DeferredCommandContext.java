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
package io.zeebe.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

public class DeferredCommandContext {
  protected final ManyToOneConcurrentLinkedQueue<Runnable> cmdQueue;
  protected final Consumer<Runnable> cmdConsumer = Runnable::run;

  public DeferredCommandContext() {
    this.cmdQueue = new ManyToOneConcurrentLinkedQueue<>();
  }

  public <T> CompletableFuture<T> runAsync(Consumer<CompletableFuture<T>> action) {
    final CompletableFuture<T> future = new CompletableFuture<>();

    cmdQueue.add(
        () -> {
          try {
            action.accept(future);
          } catch (Exception e) {
            future.completeExceptionally(e);
          }
        });
    return future;
  }

  /** Use this when no future is required. */
  public void runAsync(Runnable r) {
    cmdQueue.add(r);
  }

  public void doWork() {
    while (!cmdQueue.isEmpty()) {
      final Runnable runnable = cmdQueue.poll();
      if (runnable != null) {
        cmdConsumer.accept(runnable);
      }
    }
  }
}
