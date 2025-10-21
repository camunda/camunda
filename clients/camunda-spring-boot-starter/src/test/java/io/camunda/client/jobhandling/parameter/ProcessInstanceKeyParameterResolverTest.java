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
package io.camunda.client.jobhandling.parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProcessInstanceKeyParameterResolverTest {
  @Mock ActivatedJob activatedJob;

  @BeforeEach
  public void setup() {
    when(activatedJob.getProcessInstanceKey()).thenReturn(123L);
  }

  @Test
  void shouldResolveLong() {
    final ParameterResolver parameterResolver =
        new ProcessInstanceKeyParameterResolver(KeyTargetType.LONG);
    final Object parameter = parameterResolver.resolve(mock(JobClient.class), activatedJob);
    assertThat(parameter).isInstanceOf(Long.class).isEqualTo(123L);
  }

  @Test
  void shouldResolveString() {
    final ParameterResolver parameterResolver =
        new ProcessInstanceKeyParameterResolver(KeyTargetType.STRING);
    final Object parameter = parameterResolver.resolve(mock(JobClient.class), activatedJob);
    assertThat(parameter).isInstanceOf(String.class).isEqualTo("123");
  }
}
