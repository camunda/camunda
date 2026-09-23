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
import io.camunda.process.test.api.testCases.ImmutableElementSelector;
import io.camunda.process.test.api.testCases.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.testCases.instructions.ImmutableTriggerTimerInstruction;
import io.camunda.process.test.api.testCases.instructions.TriggerTimerInstruction;
import io.camunda.process.test.impl.testCases.instructions.TriggerTimerInstructionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HeldTimerInstructionsTest {

  private static final String ELEMENT_ID = "escalation";
  private static final String PROCESS_DEFINITION_ID = "order-process";
  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long OTHER_PROCESS_INSTANCE_KEY = 200L;

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  private final CreatedProcessInstanceRegistry createdProcessInstances =
      new CreatedProcessInstanceRegistry();

  private final TriggerTimerInstructionHandler instructionHandler =
      new TriggerTimerInstructionHandler(createdProcessInstances);

  private final TriggerTimerInstruction triggerTimer =
      ImmutableTriggerTimerInstruction.builder()
          .processInstanceSelector(
              ImmutableProcessInstanceSelector.builder()
                  .processDefinitionId(PROCESS_DEFINITION_ID)
                  .build())
          .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
          .build();

  @Test
  void shouldTriggerHeldTimerOfTheInstanceTheTestCaseCreated() {
    // given
    createdProcessInstances.register(PROCESS_DEFINITION_ID, PROCESS_INSTANCE_KEY, true, true);

    // when
    instructionHandler.execute(triggerTimer, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).triggerTimer(PROCESS_INSTANCE_KEY, ELEMENT_ID);
  }

  @Test
  void shouldIgnoreAnInstanceOfAnotherProcess() {
    // given
    createdProcessInstances.register("other-process", OTHER_PROCESS_INSTANCE_KEY, true, true);
    createdProcessInstances.register(PROCESS_DEFINITION_ID, PROCESS_INSTANCE_KEY, true, true);

    // when
    instructionHandler.execute(triggerTimer, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).triggerTimer(PROCESS_INSTANCE_KEY, ELEMENT_ID);
  }

  @Test
  void shouldRejectWhenTheTestCaseCreatedNoInstanceOfTheProcess() {
    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    triggerTimer, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(PROCESS_DEFINITION_ID)
        .hasMessageContaining("this test case created no such process instance")
        .hasMessageContaining("called process");
  }

  @Test
  void shouldRejectWhenTheInstanceWasCreatedWithoutHeldTimers() {
    // given an instance whose timers the engine schedules as usual
    createdProcessInstances.register(PROCESS_DEFINITION_ID, PROCESS_INSTANCE_KEY, false, false);

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    triggerTimer, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("this test case created no such process instance");
  }

  @Test
  void shouldRejectWhenTwoInstancesOfTheProcessHoldTimers() {
    // given
    createdProcessInstances.register(PROCESS_DEFINITION_ID, PROCESS_INSTANCE_KEY, true, true);
    createdProcessInstances.register(PROCESS_DEFINITION_ID, OTHER_PROCESS_INSTANCE_KEY, true, true);

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    triggerTimer, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("created 2 process instances")
        .hasMessageContaining(String.valueOf(PROCESS_INSTANCE_KEY))
        .hasMessageContaining(String.valueOf(OTHER_PROCESS_INSTANCE_KEY));
  }

  @Test
  void shouldForgetTheInstancesOfThePreviousTestCase() {
    // given an instance created by an earlier test case of the same test
    createdProcessInstances.register(PROCESS_DEFINITION_ID, PROCESS_INSTANCE_KEY, true, true);
    createdProcessInstances.clear();

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    triggerTimer, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("this test case created no such process instance");
  }

  @Test
  void shouldRejectTimerSelectedByElementName() {
    // given an element selector that names the element instead of identifying it
    final TriggerTimerInstruction instruction =
        ImmutableTriggerTimerInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(
                ImmutableElementSelector.builder().elementName("Escalation deadline").build())
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
