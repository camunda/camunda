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
package io.camunda.zeebe.client.impl.worker;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

final class BlockingExecutor implements Executor {
  private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MILLISECONDS;

  private final Executor wrappedExecutor;
  private final Semaphore semaphore;
  private final long timeoutMillis;

  public BlockingExecutor(
      final Executor wrappedExecutor, final int maxActivate, final Duration jobActivationTimeout) {
    this.wrappedExecutor = wrappedExecutor;
    semaphore = new Semaphore(maxActivate);
    timeoutMillis = jobActivationTimeout.toMillis();
  }

  @Override
  public void execute(final Runnable command) throws RejectedExecutionException {
    try {
      if (!semaphore.tryAcquire(timeoutMillis, TIMEOUT_UNIT)) {
        throw new RejectedExecutionException(
            String.format(
                "Not able to acquire lease in %d%s", timeoutMillis, TIMEOUT_UNIT.toString()));
      }

      wrappedExecutor.execute(
          () -> {
            try {
              command.run();
            } finally {
              semaphore.release();
            }
          });
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
