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

import io.zeebe.util.Loggers;
import java.util.function.BiConsumer;

public class FutureContinuationRunnable<T> implements Runnable {
  private ActorFuture<T> future;
  private BiConsumer<T, Throwable> consumer;

  public FutureContinuationRunnable(ActorFuture<T> future, BiConsumer<T, Throwable> consumer) {
    this.future = future;
    this.consumer = consumer;
  }

  @Override
  public void run() {
    if (!future.isCompletedExceptionally()) {
      try {
        final T res = future.get();
        consumer.accept(res, null);
      } catch (Throwable e) {
        Loggers.ACTOR_LOGGER.debug("Continuing on future completion failed", e);
      }
    } else {
      consumer.accept(null, future.getException());
    }
  }
}
