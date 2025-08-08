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

import static io.camunda.spring.client.annotation.AnnotationUtil.getVariableParameters;
import static io.camunda.spring.client.annotation.AnnotationUtil.getVariableValue;
import static io.camunda.spring.client.annotation.AnnotationUtil.getVariablesAsTypeParameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.spring.client.bean.MethodInfo;
import io.camunda.spring.client.bean.ParameterInfo;
import io.camunda.spring.client.jobhandling.parameter.ParameterResolver;
import io.camunda.spring.client.jobhandling.parameter.ParameterResolverStrategy;
import io.camunda.spring.client.jobhandling.result.ResultProcessor;
import io.camunda.spring.client.jobhandling.result.ResultProcessorStrategy;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.ReflectionUtils;

public class SpringBeanJobHandlerFactory implements JobHandlerFactory {
  private final MethodInfo methodInfo;

  public SpringBeanJobHandlerFactory(final MethodInfo methodInfo) {
    this.methodInfo = methodInfo;
  }

  private List<ParameterResolver> createParameterResolvers(
      final ParameterResolverStrategy parameterResolverStrategy) {
    return methodInfo.getParameters().stream()
        .map(parameterResolverStrategy::createResolver)
        .toList();
  }

  private ResultProcessor createResultProcessor(
      final ResultProcessorStrategy resultProcessorStrategy) {
    return resultProcessorStrategy.createProcessor(methodInfo);
  }

  @Override
  public JobHandler getJobHandler(final JobHandlerFactoryContext context) {
    return new JobHandlerInvokingSpringBeans(
        context.jobWorkerValue().getName(),
        context.jobWorkerValue().getMethod(),
        context.jobWorkerValue().getAutoComplete(),
        context.jobWorkerValue().getMaxRetries(),
        context.commandExceptionHandlingStrategy(),
        context.metricsRecorder(),
        createParameterResolvers(context.parameterResolverStrategy()),
        createResultProcessor(context.resultProcessorStrategy()),
        context.jobExceptionHandlingStrategy());
  }

  @Override
  public String getGeneratedJobWorkerName() {
    return methodInfo.getBeanName() + "#" + methodInfo.getMethodName();
  }

  @Override
  public String getGeneratedJobWorkerType() {
    return methodInfo.getMethodName();
  }

  @Override
  public boolean usesActivatedJob() {
    return methodInfo.getParameters().stream()
        .anyMatch(p -> p.getParameterInfo().getType().isAssignableFrom(ActivatedJob.class));
  }

  @Override
  public List<String> getUsedVariableNames() {
    final List<String> result = new ArrayList<>();
    final List<ParameterInfo> parameters = getVariablesAsTypeParameters(methodInfo);
    parameters.forEach(
        pi ->
            ReflectionUtils.doWithFields(
                pi.getParameterInfo().getType(), f -> result.add(extractFieldName(f))));
    result.addAll(
        readZeebeVariableParameters(methodInfo).stream().map(this::extractVariableName).toList());
    return result;
  }

  private List<ParameterInfo> readZeebeVariableParameters(final MethodInfo methodInfo) {
    return getVariableParameters(methodInfo);
  }

  private String extractVariableName(final ParameterInfo parameterInfo) {
    // get can be used here as the list is already filtered by readZeebeVariableParameters
    return getVariableValue(parameterInfo).get().getName();
  }

  private String extractFieldName(final Field field) {
    if (field.isAnnotationPresent(JsonProperty.class)) {
      final String value = field.getAnnotation(JsonProperty.class).value();
      if (StringUtils.isNotBlank(value)) {
        return value;
      }
    }
    return field.getName();
  }
}
