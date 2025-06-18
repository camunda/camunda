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
package io.camunda.spring.client.jobhandling.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import org.junit.jupiter.api.Test;

class DefaultResultProcessorTest {

  private final DefaultResultProcessor defaultResultProcessor =
      new DefaultResultProcessor(mock(CamundaClient.class));

  @Test
  public void testProcessMethodShouldReturnResult() {
    // Given
    final String inputValue = "input";
    final ActivatedJob job = mock(ActivatedJob.class);
    final ResultProcessorContext context = new ResultProcessorContext(inputValue, job);
    // When
    final Object resultValue = defaultResultProcessor.process(context);
    // Then
    assertThat(resultValue).isEqualTo(inputValue);
  }
}
