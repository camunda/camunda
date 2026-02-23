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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.bean.MethodInfo;
import io.camunda.client.jobhandling.result.ResultProcessorStrategy.ResultProcessorStrategyContext;
import org.junit.jupiter.api.Test;

class DefaultResultProcessorStrategyTest {

  private final DefaultResultProcessorStrategy resultProcessorStrategy =
      new DefaultResultProcessorStrategy(
          mock(DocumentResultProcessorFailureHandlingStrategy.class));

  @Test
  void createProcessorShouldReturnDefaultProcessor() throws NoSuchMethodException {
    // Given
    final MethodInfo methodInfo = mock(MethodInfo.class);
    // When
    final ResultProcessor resultProcessor =
        resultProcessorStrategy.createProcessor(
            new ResultProcessorStrategyContext(methodInfo, mock(CamundaClient.class)));
    // Then
    assertThat(resultProcessor).isInstanceOf(DefaultResultProcessor.class);
  }

  @Test
  void createProcessorForUserTaskResultFunctionShouldReturnResultFunctionProcessor() {
    // Given
    final MethodInfo methodInfo = mock(MethodInfo.class);
    when(methodInfo.getReturnType()).thenAnswer((i) -> UserTaskResultFunction.class);
    // When
    final ResultProcessor resultProcessor =
        resultProcessorStrategy.createProcessor(
            new ResultProcessorStrategyContext(methodInfo, mock(CamundaClient.class)));
    // Then
    assertThat(resultProcessor).isInstanceOf(ResultFunctionResultProcessor.class);
  }

  @Test
  void createProcessorForAdHocSubprocessTaskResultFunctionShouldReturnResultFunctionProcessor() {
    // Given
    final MethodInfo methodInfo = mock(MethodInfo.class);
    when(methodInfo.getReturnType()).thenAnswer((i) -> AdHocSubProcessResultFunction.class);
    // When
    final ResultProcessor resultProcessor =
        resultProcessorStrategy.createProcessor(
            new ResultProcessorStrategyContext(methodInfo, mock(CamundaClient.class)));
    // Then
    assertThat(resultProcessor).isInstanceOf(ResultFunctionResultProcessor.class);
  }
}
