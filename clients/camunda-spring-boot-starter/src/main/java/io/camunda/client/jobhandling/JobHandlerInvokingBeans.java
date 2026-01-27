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
package io.camunda.client.jobhandling;

import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.impl.Loggers;
import io.camunda.client.jobhandling.parameter.ParameterResolver;
import io.camunda.client.jobhandling.result.ResultProcessor;
import io.camunda.client.jobhandling.result.ResultProcessorContext;
import io.camunda.client.metrics.JobHandlerMetrics;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.metrics.MetricsRecorder.CounterMetricsContext;
import java.util.List;
import org.slf4j.Logger;

/** Zeebe JobHandler that invokes a bean */
public class JobHandlerInvokingBeans implements JobHandler {

  private static final Logger LOG = Loggers.JOB_WORKER_LOGGER;
  private final String jobWorkerName;
  private final BeanMethod method;
  private final boolean autoComplete;
  private final int maxRetries;
  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final MetricsRecorder metricsRecorder;
  private final List<ParameterResolver> parameterResolvers;
  private final ResultProcessor resultProcessor;

  public JobHandlerInvokingBeans(
      final String jobWorkerName,
      final BeanMethod method,
      final boolean autoComplete,
      final int maxRetries,
      final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      final MetricsRecorder metricsRecorder,
      final List<ParameterResolver> parameterResolvers,
      final ResultProcessor resultProcessor) {
    this.jobWorkerName = jobWorkerName;
    this.method = method;
    this.autoComplete = autoComplete;
    this.maxRetries = maxRetries;
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.metricsRecorder = metricsRecorder;
    this.parameterResolvers = parameterResolvers;
    this.resultProcessor = resultProcessor;
  }

  @Override
  public void handle(final JobClient jobClient, final ActivatedJob job) throws Exception {
    final CounterMetricsContext counterMetricsContext = JobHandlerMetrics.counter(job);
    final List<Object> args = createParameters(jobClient, job);
    LOG.trace("Handle {} and invoke worker {}", job, jobWorkerName);
    metricsRecorder.increaseActivated(counterMetricsContext);
    final Object methodInvocationResult =
        metricsRecorder.executeWithTimer(
            JobHandlerMetrics.timer(job), () -> method.invoke(args.toArray()));
    final Object result =
        resultProcessor.process(new ResultProcessorContext(methodInvocationResult, job));
    if (autoComplete) {
      LOG.trace("Auto completing {}", job);
      final CommandWrapper command =
          createCommandWrapper(
              createCompleteCommand(jobClient, job, result), job, counterMetricsContext);
      command.executeAsyncWithMetrics(MetricsRecorder::increaseCompleted);
    } else {
      if (result != null) {
        LOG.warn("Result provided but auto complete disabled for job {}", job);
      }
    }
  }

  private CommandWrapper createCommandWrapper(
      final FinalCommandStep<?> command,
      final ActivatedJob job,
      final CounterMetricsContext metricsContext) {
    return new CommandWrapper(
        command,
        job,
        commandExceptionHandlingStrategy,
        metricsRecorder,
        metricsContext,
        maxRetries);
  }

  private List<Object> createParameters(final JobClient jobClient, final ActivatedJob job) {
    return parameterResolvers.stream().map(resolver -> resolver.resolve(jobClient, job)).toList();
  }

  private FinalCommandStep<CompleteJobResponse> createCompleteCommand(
      final JobClient jobClient, final ActivatedJob job, final Object result) {
    final CompleteJobCommandStep1 completeCommand = jobClient.newCompleteCommand(job.getKey());
    if (result instanceof final UserTaskResultFunction resultFunction) {
      return completeCommand.withResult(r -> resultFunction.apply(r.forUserTask()));
    } else if (result instanceof final AdHocSubProcessResultFunction resultFunction) {
      final CompleteJobCommandStep1 step1 =
          completeCommand.withResult(r -> resultFunction.apply(r.forAdHocSubProcess()));
      if (resultFunction.getVariables() != null) {
        JobHandlingUtil.applyVariables(resultFunction.getVariables(), completeCommand);
      }
      return step1;
    } else {
      return JobHandlingUtil.applyVariables(result, completeCommand);
    }
  }
}
