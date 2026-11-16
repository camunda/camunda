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
package io.camunda.process.test.impl.testCases;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.testCases.ImmutableElementSelector;
import io.camunda.process.test.api.testCases.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.testCases.instructions.ImmutableTriggerTimerInstruction;
import io.camunda.process.test.api.testCases.instructions.TriggerTimerInstruction;
import io.camunda.process.test.impl.testCases.instructions.TriggerTimerInstructionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TriggerTimerInstructionHandlerTest {

  private static final String ELEMENT_ID = "escalation";
  private static final String PROCESS_DEFINITION_ID = "order-process";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  private final TriggerTimerInstructionHandler instructionHandler =
      new TriggerTimerInstructionHandler(TestRunContext.disabled());

  @Test
  void shouldTriggerTheTimerOfTheSelectedProcessInstance() {
    // given
    final TriggerTimerInstruction instruction =
        ImmutableTriggerTimerInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    final ArgumentCaptor<ProcessInstanceSelector> selectorCaptor =
        ArgumentCaptor.forClass(ProcessInstanceSelector.class);
    verify(processTestContext)
        .triggerTimer(selectorCaptor.capture(), org.mockito.ArgumentMatchers.eq(ELEMENT_ID));
    org.assertj.core.api.Assertions.assertThat(selectorCaptor.getValue().describe())
        .contains(PROCESS_DEFINITION_ID);
  }

  @Test
  void shouldRejectAnElementNotSelectedByElementId() {
    // given
    final TriggerTimerInstruction instruction =
        ImmutableTriggerTimerInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementName("Escalation").build())
            .build();

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("elementId");
  }
}
