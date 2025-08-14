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
package io.camunda.spring.client.jobhandling;

import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.impl.Loggers;
import io.camunda.spring.client.jobhandling.JobExceptionHandlingStrategy.ExceptionHandlingContext;
import io.camunda.spring.client.jobhandling.parameter.ParameterResolver;
import io.camunda.spring.client.jobhandling.result.ResultProcessor;
import io.camunda.spring.client.jobhandling.result.ResultProcessorContext;
import io.camunda.spring.client.metrics.MetricsRecorder;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.util.function.ThrowingFunction;

/** Zeebe JobHandler that invokes a Spring bean */
public class JobHandlerInvokingSpringBeans implements JobHandler {

  private static final Logger LOG = Loggers.JOB_WORKER_LOGGER;
  private final String jobWorkerName;
  private final ThrowingFunction<Object[], Object> method;
  private final boolean autoComplete;
  private final int maxRetries;
  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final MetricsRecorder metricsRecorder;
  private final List<ParameterResolver> parameterResolvers;
  private final ResultProcessor resultProcessor;
  private final JobExceptionHandlingStrategy jobExceptionHandlingStrategy;

  public JobHandlerInvokingSpringBeans(
      final String jobWorkerName,
      final ThrowingFunction<Object[], Object> method,
      final boolean autoComplete,
      final int maxRetries,
      final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      final MetricsRecorder metricsRecorder,
      final List<ParameterResolver> parameterResolvers,
      final ResultProcessor resultProcessor,
      final JobExceptionHandlingStrategy jobExceptionHandlingStrategy) {
    this.jobWorkerName = jobWorkerName;
    this.method = method;
    this.autoComplete = autoComplete;
    this.maxRetries = maxRetries;
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.metricsRecorder = metricsRecorder;
    this.parameterResolvers = parameterResolvers;
    this.resultProcessor = resultProcessor;
    this.jobExceptionHandlingStrategy = jobExceptionHandlingStrategy;
  }

  @Override
  public void handle(final JobClient jobClient, final ActivatedJob job) throws Exception {
    final List<Object> args = createParameters(jobClient, job);
    LOG.trace("Handle {} and invoke worker {}", job, jobWorkerName);
    metricsRecorder.increase(
        MetricsRecorder.METRIC_NAME_JOB, MetricsRecorder.ACTION_ACTIVATED, job.getType());
    try {
      final Object methodInvocationResult = method.apply(args.toArray());
      final Object result =
          resultProcessor.process(new ResultProcessorContext(methodInvocationResult, job));
      if (autoComplete) {
        LOG.trace("Auto completing {}", job);
        final CommandWrapper command =
            createCommandWrapper(createCompleteCommand(jobClient, job, result), job);
        command.executeAsyncWithMetrics(
            MetricsRecorder.METRIC_NAME_JOB, MetricsRecorder.ACTION_COMPLETED, job.getType());
      } else {
        if (result != null) {
          LOG.warn("Result provided but auto complete disabled for job {}", job);
        }
      }
    } catch (final Exception e) {
      jobExceptionHandlingStrategy.handleException(
          e, new ExceptionHandlingContext(jobClient, job, maxRetries));
    }
  }

  private CommandWrapper createCommandWrapper(
      final FinalCommandStep<?> command, final ActivatedJob job) {
    return new CommandWrapper(
        command, job, commandExceptionHandlingStrategy, metricsRecorder, maxRetries);
  }

  private List<Object> createParameters(final JobClient jobClient, final ActivatedJob job) {
    return parameterResolvers.stream().map(resolver -> resolver.resolve(jobClient, job)).toList();
  }

  private FinalCommandStep<CompleteJobResponse> createCompleteCommand(
      final JobClient jobClient, final ActivatedJob job, final Object result) {
    final CompleteJobCommandStep1 completeCommand = jobClient.newCompleteCommand(job.getKey());
    return JobHandlingUtil.applyVariables(result, completeCommand);
  }
}
