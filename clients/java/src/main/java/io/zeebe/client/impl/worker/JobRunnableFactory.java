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
package io.zeebe.client.impl.worker;

import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.worker.JobClient;
import io.zeebe.client.api.worker.JobHandler;
import io.zeebe.client.impl.Loggers;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.slf4j.Logger;

public final class JobRunnableFactory {

  private static final Logger LOG = Loggers.JOB_WORKER_LOGGER;

  private final JobClient jobClient;
  private final JobHandler handler;

  public JobRunnableFactory(final JobClient jobClient, final JobHandler handler) {
    this.jobClient = jobClient;
    this.handler = handler;
  }

  public Runnable create(final ActivatedJob job, final Runnable doneCallback) {
    return () -> executeJob(job, doneCallback);
  }

  private void executeJob(final ActivatedJob job, final Runnable doneCallback) {
    try {
      handler.handle(jobClient, job);
    } catch (final Exception e) {
      LOG.warn(
          "Worker {} failed to handle job with key {} of type {}, sending fail command to broker",
          job.getWorker(),
          job.getKey(),
          job.getType(),
          e);
      final StringWriter stringWriter = new StringWriter();
      final PrintWriter printWriter = new PrintWriter(stringWriter);
      e.printStackTrace(printWriter);
      final String message = stringWriter.toString();
      jobClient
          .newFailCommand(job.getKey())
          .retries(job.getRetries() - 1)
          .errorMessage(message)
          .send();
    } finally {
      doneCallback.run();
    }
  }
}
