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
package io.camunda.client.jobhandling.result;

import io.camunda.client.CamundaClient;
import io.camunda.client.bean.MethodInfo;
import io.camunda.client.jobhandling.UserTaskResultFunction;

public class DefaultResultProcessorStrategy implements ResultProcessorStrategy {
  private final CamundaClient camundaClient;
  private final DocumentResultProcessorFailureHandlingStrategy
      documentResultProcessorFailureHandlingStrategy;

  public DefaultResultProcessorStrategy(
      final CamundaClient camundaClient,
      final DocumentResultProcessorFailureHandlingStrategy
          documentResultProcessorFailureHandlingStrategy) {
    this.camundaClient = camundaClient;
    this.documentResultProcessorFailureHandlingStrategy =
        documentResultProcessorFailureHandlingStrategy;
  }

  @Override
  public ResultProcessor createProcessor(final MethodInfo methodInfo) {
    if (methodInfo.getReturnType() != null
        && methodInfo.getReturnType().equals(UserTaskResultFunction.class)) {
      return new ResultFunctionResultProcessor();
    }
    return new DefaultResultProcessor(
        camundaClient, documentResultProcessorFailureHandlingStrategy);
  }
}
