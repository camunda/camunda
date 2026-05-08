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
package io.camunda.process.test.api.coverage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ProcessCoverageBuilderTest {

  @Test
  void shouldCollectCoverageForRunWithBuilderConfiguration() {
    // given
    final CoverageDataSource dataSource = mock(CoverageDataSource.class);
    when(dataSource.findProcessInstances()).thenReturn(Collections.<ProcessInstance>emptyList());
    when(dataSource.findDecisionInstances(any()))
        .thenReturn(Collections.<DecisionInstance>emptyList());

    final ProcessCoverage processCoverage =
        ProcessCoverage.newBuilder()
            .testClass(getClass())
            .excludeProcessDefinitionIds(Collections.singletonList("excluded-process"))
            .excludeDecisionDefinitionIds(Collections.singletonList("excluded-decision"))
            .dataSource(() -> dataSource)
            .build();

    // when / then
    assertThatCode(() -> processCoverage.collectTestRunCoverage("run-1"))
        .doesNotThrowAnyException();
  }
}
