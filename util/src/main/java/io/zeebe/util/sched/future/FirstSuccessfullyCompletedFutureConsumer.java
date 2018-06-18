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
package io.zeebe.util.sched.future;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FirstSuccessfullyCompletedFutureConsumer<T> implements BiConsumer<T, Throwable> {
  private final BiConsumer<T, Throwable> callback;
  private final Consumer<T> closer;
  private boolean isCompleted = false;
  private int pendingFutures;

  public FirstSuccessfullyCompletedFutureConsumer(
      int pendingFutures, BiConsumer<T, Throwable> callback, Consumer<T> closer) {
    this.pendingFutures = pendingFutures;
    this.callback = callback;
    this.closer = closer;
  }

  @Override
  public void accept(T result, Throwable failure) {
    pendingFutures -= 1;

    if (failure == null) {
      if (!isCompleted) {
        isCompleted = true;

        callback.accept(result, null);
      } else if (closer != null) {
        closer.accept(result);
      }
    } else {
      if (pendingFutures == 0) {
        callback.accept(null, failure);
      }
    }
  }
}
