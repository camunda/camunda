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

import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobExceptionHandler;
import io.camunda.client.impl.Loggers;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.function.Function;
import org.slf4j.Logger;

public class JobExceptionHandlerImpl implements JobExceptionHandler {
  public static final Function<JobExceptionHandlerContext, String> DEFAULT_ERROR_MESSAGE_PROVIDER =
      ctx -> {
        final StringWriter stringWriter = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(stringWriter);
        ctx.getException().printStackTrace(printWriter);
        return stringWriter.toString();
      };
  public static final Function<JobExceptionHandlerContext, Integer> DEFAULT_RETRIES_PROVIDER =
      ctx -> ctx.getActivatedJob().getRetries() - 1;
  public static final Function<JobExceptionHandlerContext, Duration>
      DEFAULT_RETRY_BACKOFF_SUPPLIER = ctx -> Duration.ZERO;
  public static final Function<JobExceptionHandlerContext, Object> DEFAULT_VARIABLES_PROVIDER =
      ctx -> null;

  private static final Logger LOG = Loggers.JOB_WORKER_LOGGER;
  private final Function<JobExceptionHandlerContext, String> errorMessageProvider;
  private final Function<JobExceptionHandlerContext, Integer> retriesProvider;
  private final Function<JobExceptionHandlerContext, Duration> retryBackoffProvider;
  private final Function<JobExceptionHandlerContext, Object> variablesProvider;

  public JobExceptionHandlerImpl(
      final Function<JobExceptionHandlerContext, String> errorMessageProvider,
      final Function<JobExceptionHandlerContext, Integer> retriesProvider,
      final Function<JobExceptionHandlerContext, Duration> retryBackoffProvider,
      final Function<JobExceptionHandlerContext, Object> variablesProvider) {
    this.errorMessageProvider = errorMessageProvider;
    this.retriesProvider = retriesProvider;
    this.retryBackoffProvider = retryBackoffProvider;
    this.variablesProvider = variablesProvider;
  }

  public JobExceptionHandlerImpl() {
    this(
        DEFAULT_ERROR_MESSAGE_PROVIDER,
        DEFAULT_RETRIES_PROVIDER,
        DEFAULT_RETRY_BACKOFF_SUPPLIER,
        DEFAULT_VARIABLES_PROVIDER);
  }

  public JobExceptionHandlerImpl(final Duration retryBackoff) {
    this(
        DEFAULT_ERROR_MESSAGE_PROVIDER,
        DEFAULT_RETRIES_PROVIDER,
        ctx -> retryBackoff,
        DEFAULT_VARIABLES_PROVIDER);
  }

  @Override
  public void handleJobException(final JobExceptionHandlerContext context) {
    final ActivatedJob job = context.getActivatedJob();
    final Exception e = context.getException();
    final JobClient jobClient = context.getJobClient();
    LOG.warn(
        "Worker {} failed to handle job with key {} of type {}, sending fail command to broker",
        job.getWorker(),
        job.getKey(),
        job.getType(),
        e);
    final Object variables = variablesProvider.apply(context);
    final FailJobCommandStep2 failJobCommandStep2 =
        jobClient
            .newFailCommand(job.getKey())
            .retries(retriesProvider.apply(context))
            .errorMessage(errorMessageProvider.apply(context))
            .retryBackoff(retryBackoffProvider.apply(context));
    if (variables != null) {
      failJobCommandStep2.variables(variables);
    }
    failJobCommandStep2.send();
  }
}
