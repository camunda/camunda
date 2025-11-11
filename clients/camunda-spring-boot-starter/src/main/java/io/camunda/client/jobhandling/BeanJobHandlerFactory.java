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

import static io.camunda.client.spring.properties.CamundaClientJobWorkerProperties.DEFAULT_AUTO_COMPLETE;
import static io.camunda.client.spring.properties.CamundaClientJobWorkerProperties.DEFAULT_MAX_RETRIES;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.bean.MethodInfo;
import io.camunda.client.jobhandling.parameter.ParameterResolver;
import io.camunda.client.jobhandling.parameter.ParameterResolverStrategy;
import io.camunda.client.jobhandling.parameter.ParameterResolverStrategy.ParameterResolverStrategyContext;
import io.camunda.client.jobhandling.result.ResultProcessor;
import io.camunda.client.jobhandling.result.ResultProcessorStrategy;
import io.camunda.client.jobhandling.result.ResultProcessorStrategy.ResultProcessorStrategyContext;
import io.camunda.client.metrics.MetricsRecorder;
import java.util.List;

public class BeanJobHandlerFactory implements JobHandlerFactory {
  private final MethodInfo methodInfo;
  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final ParameterResolverStrategy parameterResolverStrategy;
  private final ResultProcessorStrategy resultProcessorStrategy;
  private final MetricsRecorder metricsRecorder;

  public BeanJobHandlerFactory(
      final MethodInfo methodInfo,
      final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      final ParameterResolverStrategy parameterResolverStrategy,
      final ResultProcessorStrategy resultProcessorStrategy,
      final MetricsRecorder metricsRecorder) {
    this.methodInfo = methodInfo;
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.parameterResolverStrategy = parameterResolverStrategy;
    this.resultProcessorStrategy = resultProcessorStrategy;
    this.metricsRecorder = metricsRecorder;
  }

  private List<ParameterResolver> createParameterResolvers(final CamundaClient camundaClient) {
    return methodInfo.getParameters().stream()
        .map(parameterInfo -> new ParameterResolverStrategyContext(parameterInfo, camundaClient))
        .map(parameterResolverStrategy::createResolver)
        .toList();
  }

  private ResultProcessor createResultProcessor(final CamundaClient camundaClient) {
    return resultProcessorStrategy.createProcessor(
        new ResultProcessorStrategyContext(methodInfo, camundaClient));
  }

  @Override
  public JobHandler getJobHandler(final JobHandlerFactoryContext context) {
    final boolean autoComplete =
        context.jobWorkerValue().getAutoComplete().value() != null
            ? context.jobWorkerValue().getAutoComplete().value()
            : DEFAULT_AUTO_COMPLETE;
    final int maxRetries =
        context.jobWorkerValue().getMaxRetries().value() != null
            ? context.jobWorkerValue().getMaxRetries().value()
            : DEFAULT_MAX_RETRIES;
    return new JobHandlerInvokingBeans(
        context.jobWorkerValue().getName().value(),
        methodInfo::invoke,
        autoComplete,
        maxRetries,
        commandExceptionHandlingStrategy,
        metricsRecorder,
        createParameterResolvers(context.camundaClient()),
        createResultProcessor(context.camundaClient()));
  }
}
