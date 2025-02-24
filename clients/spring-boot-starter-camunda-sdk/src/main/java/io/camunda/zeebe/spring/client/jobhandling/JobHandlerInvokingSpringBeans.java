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
package io.camunda.zeebe.spring.client.jobhandling;

import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.CompleteJobResponse;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.impl.Loggers;
import io.camunda.zeebe.spring.client.annotation.value.ZeebeWorkerValue;
import io.camunda.zeebe.spring.client.jobhandling.parameter.ParameterResolver;
import io.camunda.zeebe.spring.client.jobhandling.parameter.ParameterResolverStrategy;
import io.camunda.zeebe.spring.client.jobhandling.result.ResultProcessor;
import io.camunda.zeebe.spring.client.jobhandling.result.ResultProcessorContext;
import io.camunda.zeebe.spring.client.jobhandling.result.ResultProcessorStrategy;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import io.camunda.zeebe.spring.common.exception.ZeebeBpmnError;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/** Zeebe JobHandler that invokes a Spring bean */
public class JobHandlerInvokingSpringBeans implements JobHandler {

  private static final Logger LOG = Loggers.JOB_WORKER_LOGGER;
  private final ZeebeWorkerValue workerValue;
  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final MetricsRecorder metricsRecorder;
  private final List<ParameterResolver> parameterResolvers;
  private final ResultProcessor resultProcessor;

  public JobHandlerInvokingSpringBeans(
      final ZeebeWorkerValue workerValue,
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
      }
    } catch (final ZeebeBpmnError bpmnError) {
      LOG.trace("Catched BPMN error on {}", job);
      final CommandWrapper command =
          new CommandWrapper(
              createThrowErrorCommand(jobClient, job, bpmnError),
              job,
              commandExceptionHandlingStrategy,
              metricsRecorder,
              workerValue.getMaxRetries());
      command.executeAsyncWithMetrics(
          MetricsRecorder.METRIC_NAME_JOB, MetricsRecorder.ACTION_BPMN_ERROR, job.getType());
    }
  }

  private List<Object> createParameters(final JobClient jobClient, final ActivatedJob job) {
    return parameterResolvers.stream().map(resolver -> resolver.resolve(jobClient, job)).toList();
  }

  private FinalCommandStep<CompleteJobResponse> createCompleteCommand(
      final JobClient jobClient, final ActivatedJob job, final Object result) {
    CompleteJobCommandStep1 completeCommand = jobClient.newCompleteCommand(job.getKey());
    if (result != null) {
      if (result.getClass().isAssignableFrom(Map.class)) {
        completeCommand = completeCommand.variables((Map) result);
      } else if (result.getClass().isAssignableFrom(String.class)) {
        completeCommand = completeCommand.variables((String) result);
      } else if (result.getClass().isAssignableFrom(InputStream.class)) {
        completeCommand = completeCommand.variables((InputStream) result);
      } else {
        completeCommand = completeCommand.variables(result);
      }
    }
    return completeCommand;
  }

  private FinalCommandStep<Void> createThrowErrorCommand(
      final JobClient jobClient, final ActivatedJob job, final ZeebeBpmnError bpmnError) {
    final ThrowErrorCommandStep2 command =
        jobClient
            .newThrowErrorCommand(job.getKey())
            .errorCode(bpmnError.getErrorCode())
            .errorMessage(bpmnError.getErrorMessage());
    if (bpmnError.getVariables() != null) {
      command.variables(bpmnError.getVariables());
    }
    return command;
  }
}
