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
package io.camunda.client.api.worker;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.impl.worker.JobExceptionHandlerImpl;
import java.time.Duration;

/**
 * The {@link JobWorker} uses this interface to handle exceptions that are thrown from the {@link
 * JobHandler#handle(JobClient, ActivatedJob)} method.
 */
@FunctionalInterface
public interface JobExceptionHandler {
  void handleJobException(JobExceptionHandlerContext context);

  /**
   * @return the default job exception handler
   */
  static JobExceptionHandler createDefault() {
    return new JobExceptionHandlerImpl();
  }

  static JobExceptionHandler createWithRetryBackoff(final Duration retryBackoff) {
    return new JobExceptionHandlerImpl(retryBackoff);
  }

  class JobExceptionHandlerContext {
    private final JobClient jobClient;
    private final ActivatedJob activatedJob;
    private final Exception exception;

    public JobExceptionHandlerContext(
        final JobClient jobClient, final ActivatedJob activatedJob, final Exception exception) {
      this.jobClient = jobClient;
      this.activatedJob = activatedJob;
      this.exception = exception;
    }

    public JobClient getJobClient() {
      return jobClient;
    }

    public ActivatedJob getActivatedJob() {
      return activatedJob;
    }

    public Exception getException() {
      return exception;
    }
  }
}
