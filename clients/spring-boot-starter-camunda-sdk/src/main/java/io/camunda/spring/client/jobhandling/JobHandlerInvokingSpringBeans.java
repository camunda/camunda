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
import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.impl.Loggers;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.exception.BpmnError;
import io.camunda.spring.client.exception.JobError;
import io.camunda.spring.client.jobhandling.parameter.ParameterResolver;
import io.camunda.spring.client.jobhandling.parameter.ParameterResolverStrategy;
import io.camunda.spring.client.jobhandling.result.ResultProcessor;
import io.camunda.spring.client.jobhandling.result.ResultProcessorContext;
import io.camunda.spring.client.jobhandling.result.ResultProcessorStrategy;
import io.camunda.spring.client.metrics.MetricsRecorder;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;

/** Zeebe JobHandler that invokes a Spring bean */
public class JobHandlerInvokingSpringBeans implements JobHandler {

  private static final Logger LOG = Loggers.JOB_WORKER_LOGGER;
  private final JobWorkerValue workerValue;
  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final MetricsRecorder metricsRecorder;
  private final List<ParameterResolver> parameterResolvers;
  private final ResultProcessor resultProcessor;

  public JobHandlerInvokingSpringBeans(
      final JobWorkerValue workerValue,
      final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      final MetricsRecorder metricsRecorder,
      final ParameterResolverStrategy parameterResolverStrategy,
      final ResultProcessorStrategy resultProcessorStrategy) {
    this.workerValue = workerValue;
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.metricsRecorder = metricsRecorder;
    parameterResolvers = createParameterResolvers(parameterResolverStrategy);
    resultProcessor = createResultProcessor(resultProcessorStrategy);
  }

  private List<ParameterResolver> createParameterResolvers(
      final ParameterResolverStrategy parameterResolverStrategy) {
    return workerValue.getMethodInfo().getParameters().stream()
        .map(parameterResolverStrategy::createResolver)
        .toList();
  }

  private ResultProcessor createResultProcessor(
      final ResultProcessorStrategy resultProcessorStrategy) {
    return resultProcessorStrategy.createProcessor(workerValue.getMethodInfo());
  }

  @Override
  public void handle(final JobClient jobClient, final ActivatedJob job) throws Exception {
    final List<Object> args = createParameters(jobClient, job);
    LOG.trace("Handle {} and invoke worker {}", job, workerValue);
    try {
      metricsRecorder.increase(
          MetricsRecorder.METRIC_NAME_JOB, MetricsRecorder.ACTION_ACTIVATED, job.getType());
      final Object result;
      try {
        final Object methodInvocationResult = workerValue.getMethodInfo().invoke(args.toArray());
        result = resultProcessor.process(new ResultProcessorContext(methodInvocationResult, job));
      } catch (final Throwable t) {
        metricsRecorder.increase(
            MetricsRecorder.METRIC_NAME_JOB, MetricsRecorder.ACTION_FAILED, job.getType());
        throw t;
      }

      if (workerValue.getAutoComplete()) {
        LOG.trace("Auto completing {}", job);
        final CommandWrapper command =
            new CommandWrapper(
                createCompleteCommand(jobClient, job, result),
                job,
                commandExceptionHandlingStrategy,
                metricsRecorder,
                workerValue.getMaxRetries());
        command.executeAsyncWithMetrics(
            MetricsRecorder.METRIC_NAME_JOB, MetricsRecorder.ACTION_COMPLETED, job.getType());
      } else {
        if (result != null) {
          LOG.warn("Result provided but auto complete disabled for job {}", job);
        }
      }
    } catch (final BpmnError bpmnError) {
      LOG.trace("Caught BPMN error on {}", job);
      final CommandWrapper command =
          new CommandWrapper(
              createThrowErrorCommand(jobClient, job, bpmnError),
              job,
              commandExceptionHandlingStrategy,
              metricsRecorder,
              workerValue.getMaxRetries());
      command.executeAsyncWithMetrics(
          MetricsRecorder.METRIC_NAME_JOB, MetricsRecorder.ACTION_BPMN_ERROR, job.getType());
    } catch (final JobError jobError) {
      LOG.trace("Caught job error on {}", job);
      final CommandWrapper command =
          new CommandWrapper(
              createFailJobCommand(jobClient, job, jobError),
              job,
              commandExceptionHandlingStrategy,
              metricsRecorder,
              workerValue.getMaxRetries());
      command.executeAsync();
    }
  }

  private List<Object> createParameters(final JobClient jobClient, final ActivatedJob job) {
    return parameterResolvers.stream().map(resolver -> resolver.resolve(jobClient, job)).toList();
  }

  private FinalCommandStep<CompleteJobResponse> createCompleteCommand(
      final JobClient jobClient, final ActivatedJob job, final Object result) {
    final CompleteJobCommandStep1 completeCommand = jobClient.newCompleteCommand(job.getKey());
    return applyVariables(
        result,
        completeCommand::variables,
        completeCommand::variables,
        completeCommand::variables,
        completeCommand::variables,
        completeCommand);
  }

  private FinalCommandStep<Void> createThrowErrorCommand(
      final JobClient jobClient, final ActivatedJob job, final BpmnError bpmnError) {
    final ThrowErrorCommandStep2 command =
        jobClient
            .newThrowErrorCommand(job.getKey())
            .errorCode(bpmnError.getErrorCode())
            .errorMessage(bpmnError.getErrorMessage());
    return applyVariables(
        bpmnError.getVariables(),
        command::variables,
        command::variables,
        command::variables,
        command::variables,
        command);
  }

  private <T> T applyVariables(
      final Object variables,
      final Function<Map<String, Object>, T> mapApplier,
      final Function<String, T> stringApplier,
      final Function<InputStream, T> inputStreamApplier,
      final Function<Object, T> objectApplier,
      final T onNull) {
    if (variables == null) {
      return onNull;
    } else if (variables.getClass().isAssignableFrom(Map.class)) {
      return mapApplier.apply((Map<String, Object>) variables);
    } else if (variables.getClass().isAssignableFrom(String.class)) {
      return stringApplier.apply((String) variables);
    } else if (variables.getClass().isAssignableFrom(InputStream.class)) {
      return inputStreamApplier.apply((InputStream) variables);
    } else {
      return objectApplier.apply(variables);
    }
  }

  private FinalCommandStep<FailJobResponse> createFailJobCommand(
      final JobClient jobClient, final ActivatedJob job, final JobError jobError) {
    final int retries =
        jobError.getRetries() == null ? (job.getRetries() - 1) : jobError.getRetries();
    final StringWriter stringWriter = new StringWriter();
    final PrintWriter printWriter = new PrintWriter(stringWriter);
    jobError.printStackTrace(printWriter);
    final String message = stringWriter.toString();
    final Duration backoff = jobError.getTimeout() == null ? Duration.ZERO : jobError.getTimeout();
    final FailJobCommandStep2 command =
        jobClient
            .newFailCommand(job.getKey())
            .retries(retries)
            .errorMessage(message)
            .retryBackoff(backoff);
    return applyVariables(
        jobError.getVariables(),
        command::variables,
        command::variables,
        command::variables,
        command::variables,
        command);
  }
}
