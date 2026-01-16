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
package io.camunda.process.test.impl.dsl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.IncidentSelector;
import io.camunda.process.test.api.dsl.ImmutableIncidentSelector;
import io.camunda.process.test.api.dsl.instructions.ImmutableResolveIncidentInstruction;
import io.camunda.process.test.api.dsl.instructions.ResolveIncidentInstruction;
import io.camunda.process.test.impl.dsl.instructions.ResolveIncidentInstructionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ResolveIncidentInstructionTest {

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  @Mock private IncidentFilter incidentFilter;

  @Captor private ArgumentCaptor<IncidentSelector> selectorCaptor;

  private final ResolveIncidentInstructionHandler instructionHandler =
      new ResolveIncidentInstructionHandler();

  @Test
  void shouldResolveIncidentByElementId() {
    // given
    final ResolveIncidentInstruction instruction =
        ImmutableResolveIncidentInstruction.builder()
            .incidentSelector(ImmutableIncidentSelector.builder().elementId("task1").build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).resolveIncident(selectorCaptor.capture());

    selectorCaptor.getValue().applyFilter(incidentFilter);
    verify(incidentFilter).elementId("task1");

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldResolveIncidentByProcessDefinitionId() {
    // given
    final ResolveIncidentInstruction instruction =
        ImmutableResolveIncidentInstruction.builder()
            .incidentSelector(
                ImmutableIncidentSelector.builder().processDefinitionId("my-process").build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).resolveIncident(selectorCaptor.capture());

    selectorCaptor.getValue().applyFilter(incidentFilter);
    verify(incidentFilter).processDefinitionId("my-process");

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldResolveIncidentWithCombinedSelector() {
    // given
    final ResolveIncidentInstruction instruction =
        ImmutableResolveIncidentInstruction.builder()
            .incidentSelector(
                ImmutableIncidentSelector.builder()
                    .processDefinitionId("my-process")
                    .elementId("task1")
                    .build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).resolveIncident(selectorCaptor.capture());

    selectorCaptor.getValue().applyFilter(incidentFilter);
    verify(incidentFilter).elementId("task1");
    verify(incidentFilter).processDefinitionId("my-process");

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }
}
