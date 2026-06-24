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
package io.camunda.client.impl.worker;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobExceptionHandler;
import io.camunda.client.api.worker.JobExceptionHandler.JobExceptionHandlerContext;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.impl.Loggers;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.MDC.MDCCloseable;

public final class JobRunnableFactoryImpl implements JobRunnableFactory {
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String ELEMENT_INSTANCE_KEY = "elementInstanceKey";
  public static final String JOB_KEY = "jobKey";
  private static final Logger LOG = Loggers.JOB_WORKER_LOGGER;
  private final JobClient jobClient;
  private final JobHandler handler;
  private final JobExceptionHandler jobExceptionHandler;

  public JobRunnableFactoryImpl(
      final JobClient jobClient,
      final JobHandler handler,
      final JobExceptionHandler jobExceptionHandler) {
    this.jobClient = jobClient;
    this.handler = handler;
    this.jobExceptionHandler = jobExceptionHandler;
  }

  @Override
  public Runnable create(final ActivatedJob job, final Runnable doneCallback) {
    return () -> executeJob(job, doneCallback);
  }

  private void executeJob(final ActivatedJob job, final Runnable doneCallback) {
    try (final MDCCloseable processDefinitionKey =
            MDC.putCloseable(
                PROCESS_DEFINITION_KEY, String.valueOf(job.getProcessDefinitionKey()));
        final MDCCloseable processInstanceKey =
            MDC.putCloseable(PROCESS_INSTANCE_KEY, String.valueOf(job.getProcessInstanceKey()));
        final MDCCloseable elementInstanceKey =
            MDC.putCloseable(ELEMENT_INSTANCE_KEY, String.valueOf(job.getElementInstanceKey()));
        final MDCCloseable jobKey = MDC.putCloseable(JOB_KEY, String.valueOf(job.getKey()))) {
      handler.handle(jobClient, job);
    } catch (final Exception e) {
      jobExceptionHandler.handleJobException(new JobExceptionHandlerContext(jobClient, job, e));
    } finally {
      doneCallback.run();
    }
  }
}
