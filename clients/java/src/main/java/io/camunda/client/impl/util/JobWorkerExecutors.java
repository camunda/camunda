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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

/**
 * Executor services to be used by the job worker framework.
 *
 * <p>{@link JobWorkerExecutors#scheduledExecutor} is dedicated for scheduled tasks such as job
 * polling or stream connection management.
 *
 * <p>{@link JobWorkerExecutors#jobHandlingExecutor} is a dedicated executor for job handling only.
 *
 * <p>Each executorService may or may not be owned by the Camunda Client Job framework, indicated by
 * {@link JobWorkerExecutors#ownsScheduledExecutorResource} for the scheduledExecutor or by the
 * {@link JobWorkerExecutors#ownsJobHandlingExecutor} flag for the jobHandlingExecutor. If flagged
 * as owned, then {@link JobWorkerExecutors#close()} will shut down the service. Otherwise it's a
 * no-op.
 */
public final class JobWorkerExecutors implements AutoCloseable {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(JobWorkerExecutors.class);

  private final ScheduledExecutorService scheduledExecutor;
  private final boolean ownsScheduledExecutorResource;

  private final ExecutorService jobHandlingExecutor;
  private final boolean ownsJobHandlingExecutor;

  public JobWorkerExecutors(final ScheduledExecutorService executor, final boolean ownsResource) {
    this(executor, ownsResource, null, false);
  }

  public JobWorkerExecutors(
      final ScheduledExecutorService executor,
      final boolean ownsResource,
      final ExecutorService jobHandlingExecutor,
      final boolean ownsJobHandlingExecutor) {
    scheduledExecutor = executor;
    ownsScheduledExecutorResource = ownsResource;

    if (jobHandlingExecutor == null) {
      LOG.debug("No job handling executor provided, using scheduled executor instead.");
      this.jobHandlingExecutor = scheduledExecutor;
    } else {
      LOG.debug("Using a dedicated job handling executor.");
      this.jobHandlingExecutor = jobHandlingExecutor;
    }

    this.ownsJobHandlingExecutor = ownsJobHandlingExecutor;
  }

  public ScheduledExecutorService scheduledExecutor() {
    return scheduledExecutor;
  }

  public ExecutorService jobHandlingExecutor() {
    return jobHandlingExecutor;
  }

  @Override
  public void close() {
    closeExecutor(scheduledExecutor, ownsScheduledExecutorResource);

    if (jobHandlingExecutor != scheduledExecutor) {
      closeExecutor(jobHandlingExecutor, ownsJobHandlingExecutor);
    }
  }

  private void closeExecutor(final ExecutorService executor, final boolean ownsResource) {
    if (!ownsResource) {
      return;
    }

    executor.shutdownNow();

    try {
      if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
        throw new ClientException("Timed out awaiting termination of job worker executor");
      }
    } catch (final InterruptedException e) {
      throw new ClientException(
          "Unexpected interrupted awaiting termination of job worker executor", e);
    }
  }
}
