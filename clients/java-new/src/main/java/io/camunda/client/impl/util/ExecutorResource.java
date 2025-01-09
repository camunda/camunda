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
package io.camunda.client.impl.util;

import io.camunda.client.api.command.ClientException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Represents a shared executor service which may or may not be owned by whoever created it. If it
 * is owned, then close will shut down the service. Otherwise it's a no-op.
 */
public final class ExecutorResource implements AutoCloseable {
  private final ScheduledExecutorService executor;
  private final boolean ownsResource;

  public ExecutorResource(final ScheduledExecutorService executor, final boolean ownsResource) {
    this.executor = executor;
    this.ownsResource = ownsResource;
  }

  public ScheduledExecutorService executor() {
    return executor;
  }

  @Override
  public void close() {
    if (!ownsResource) {
      return;
    }

    executor.shutdownNow();

    try {
      if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
        throw new ClientException(
            "Timed out awaiting termination of job worker executor after 15 seconds");
      }
    } catch (final InterruptedException e) {
      throw new ClientException(
          "Unexpected interrupted awaiting termination of job worker executor", e);
    }
  }
}
