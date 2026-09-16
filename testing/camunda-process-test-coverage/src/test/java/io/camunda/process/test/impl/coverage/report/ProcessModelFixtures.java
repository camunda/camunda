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
package io.camunda.process.test.impl.coverage.report;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.process.test.api.coverage.model.ProcessModel;
import io.camunda.process.test.impl.coverage.core.ModelCreator;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageProcessDefinitionData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageTestData;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;

/**
 * Builds process models for report tests the way the coverage collector builds them at runtime, so
 * that a model's element count and its BPMN cannot drift apart.
 */
final class ProcessModelFixtures {

  private ProcessModelFixtures() {}

  static ProcessModel modelOf(final String processDefinitionId, final BpmnModelInstance bpmnModel) {
    final ProcessDefinition processDefinition = mock(ProcessDefinition.class);
    when(processDefinition.getProcessDefinitionId()).thenReturn(processDefinitionId);
    when(processDefinition.getVersion()).thenReturn(1);

    return ModelCreator.createModel(
        ImmutableCoverageTestData.builder()
            .addProcessDefinitionData(
                ImmutableCoverageProcessDefinitionData.builder()
                    .processDefinition(processDefinition)
                    .xml(Bpmn.convertToString(bpmnModel))
                    .build())
            .build(),
        processDefinitionId);
  }
}
