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
package io.camunda.spring.client.jobhandling.parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.impl.CamundaObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VariableParameterResolverTest {

  private VariableParameterResolver resolver;
  @Mock private JobClient jobClient;
  @Mock private ActivatedJob job;

  @BeforeEach
  void setUp() {
    resolver =
        new VariableParameterResolver("testVar", String.class, new CamundaObjectMapper(), true);
  }

  @Test
  void shouldResolveVariableNotPresent() {
    when(job.getVariablesAsMap()).thenReturn(Map.of("anotherVar", "another value"));

    final Object resolvedValue = resolver.resolve(jobClient, job);

    assertThat(resolvedValue).isNull();
  }

  @Test
  void shouldResolveVariableIsPresent() {
    when(job.getVariablesAsMap()).thenReturn(Map.of("testVar", "test value"));

    final Object resolvedValue = resolver.resolve(jobClient, job);

    assertThat(resolvedValue).isEqualTo("test value");
  }
}
