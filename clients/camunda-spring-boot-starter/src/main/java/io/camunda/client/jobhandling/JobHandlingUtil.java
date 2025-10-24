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

import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.api.command.CommandWithVariables;
import io.camunda.client.exception.JobError;
import io.camunda.client.jobhandling.parameter.ParameterResolver;
import io.camunda.client.jobhandling.parameter.ParameterResolverStrategy;
import io.camunda.client.jobhandling.result.ResultProcessor;
import io.camunda.client.jobhandling.result.ResultProcessorStrategy;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.exception.ExceptionUtils;

public final class JobHandlingUtil {

  private JobHandlingUtil() {}

  public static List<ParameterResolver> createParameterResolvers(
      final ParameterResolverStrategy parameterResolverStrategy, final JobWorkerValue workerValue) {
    return workerValue.getMethodInfo().getParameters().stream()
        .map(parameterResolverStrategy::createResolver)
        .toList();
  }

  public static ResultProcessor createResultProcessor(
      final ResultProcessorStrategy resultProcessorStrategy, final JobWorkerValue workerValue) {
    return resultProcessorStrategy.createProcessor(workerValue.getMethodInfo());
  }

  public static <T extends CommandWithVariables<T>> T applyVariables(
      final Object variables, final T command) {
    if (variables == null) {
      return command;
    } else if (variables.getClass().isAssignableFrom(Map.class)) {
      return command.variables((Map<String, Object>) variables);
    } else if (variables.getClass().isAssignableFrom(String.class)) {
      return command.variables((String) variables);
    } else if (variables.getClass().isAssignableFrom(InputStream.class)) {
      return command.variables((InputStream) variables);
    } else {
      return command.variables(variables);
    }
  }

  public static String createErrorMessage(final JobError jobError) {
    return ExceptionUtils.getStackTrace(jobError);
  }
}
