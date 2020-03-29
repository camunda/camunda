/*
 * Copyright 2015-present Open Networking Foundation
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
package io.atomix.utils.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Special implementation of {@link CompletableFuture} with missing utility methods.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class ComposableFuture<T> extends CompletableFuture<T> implements BiConsumer<T, Throwable> {

  @Override
  public void accept(final T result, final Throwable error) {
    if (error == null) {
      complete(result);
    } else {
      completeExceptionally(error);
    }
  }

  /**
   * Sets a consumer to be called when the future is failed.
   *
   * @param consumer The consumer to call.
   * @return A new future.
   */
  public CompletableFuture<T> except(final Consumer<Throwable> consumer) {
    return whenComplete(
        (result, error) -> {
          if (error != null) {
            consumer.accept(error);
          }
        });
  }

  /**
   * Sets a consumer to be called asynchronously when the future is failed.
   *
   * @param consumer The consumer to call.
   * @return A new future.
   */
  public CompletableFuture<T> exceptAsync(final Consumer<Throwable> consumer) {
    return whenCompleteAsync(
        (result, error) -> {
          if (error != null) {
            consumer.accept(error);
          }
        });
  }

  /**
   * Sets a consumer to be called asynchronously when the future is failed.
   *
   * @param consumer The consumer to call.
   * @param executor The executor with which to call the consumer.
   * @return A new future.
   */
  public CompletableFuture<T> exceptAsync(
      final Consumer<Throwable> consumer, final Executor executor) {
    return whenCompleteAsync(
        (result, error) -> {
          if (error != null) {
            consumer.accept(error);
          }
        },
        executor);
  }
}
