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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.behavior.BehaviorCondition;
import io.camunda.process.test.api.behavior.ConditionalBehaviorBuilder;
import io.camunda.process.test.api.testCases.ImmutableElementSelector;
import io.camunda.process.test.api.testCases.ImmutableJobSelector;
import io.camunda.process.test.api.testCases.ImmutableProcessDefinitionSelector;
import io.camunda.process.test.api.testCases.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.testCases.ImmutableUserTaskSelector;
import io.camunda.process.test.api.testCases.instructions.AssertElementInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.AssertUserTaskInstruction;
import io.camunda.process.test.api.testCases.instructions.CompleteJobInstruction;
import io.camunda.process.test.api.testCases.instructions.CompleteUserTaskInstruction;
import io.camunda.process.test.api.testCases.instructions.ConditionalBehaviorInstruction;
import io.camunda.process.test.api.testCases.instructions.CreateProcessInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableAssertElementInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableAssertProcessInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableAssertUserTaskInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableCompleteJobInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableCompleteUserTaskInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableConditionalBehaviorInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableCreateProcessInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.assertElementInstance.ElementInstanceState;
import io.camunda.process.test.api.testCases.instructions.assertUserTask.UserTaskState;
import io.camunda.process.test.impl.testCases.instructions.ConditionalBehaviorInstructionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ConditionalBehaviorInstructionTest {

  private static final AssertElementInstanceInstruction CONDITION_ELEMENT_ACTIVE =
      ImmutableAssertElementInstanceInstruction.builder()
          .processInstanceSelector(
              ImmutableProcessInstanceSelector.builder().processDefinitionId("p").build())
          .elementSelector(ImmutableElementSelector.builder().elementId("task1").build())
          .state(ElementInstanceState.IS_ACTIVE)
          .build();

  private static final AssertUserTaskInstruction CONDITION_USER_TASK_CREATED =
      ImmutableAssertUserTaskInstruction.builder()
          .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
          .state(UserTaskState.IS_CREATED)
          .build();

  private static final CompleteUserTaskInstruction ACTION_COMPLETE_USER_TASK =
      ImmutableCompleteUserTaskInstruction.builder()
          .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
          .build();

  private static final CompleteJobInstruction ACTION_COMPLETE_JOB =
      ImmutableCompleteJobInstruction.builder()
          .jobSelector(ImmutableJobSelector.builder().jobType("type").build())
          .build();

  @Mock private CamundaProcessTestContext processTestContext;
  @Mock private ConditionalBehaviorBuilder builder;
  @Mock private CamundaClient camundaClient;
  @Mock private AssertionFacade assertionFacade;
  @Mock private TestCaseInstructionHandlerRegistry registry;

  @Captor private ArgumentCaptor<BehaviorCondition> conditionCaptor;
  @Captor private ArgumentCaptor<Runnable> runnableCaptor;

  private ConditionalBehaviorInstructionHandler instructionHandler;

  @BeforeEach
  void setUp() {
    instructionHandler = new ConditionalBehaviorInstructionHandler(registry);
    when(processTestContext.when(any(BehaviorCondition.class))).thenReturn(builder);
    when(builder.as(anyString())).thenReturn(builder);
    when(builder.then(any(Runnable.class))).thenReturn(builder);
  }

  @Test
  void shouldRegisterBehaviorWithSingleCondition() {
    // given
    final ConditionalBehaviorInstruction instruction =
        ImmutableConditionalBehaviorInstruction.builder()
            .addConditions(CONDITION_ELEMENT_ACTIVE)
            .addActions(ACTION_COMPLETE_USER_TASK)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).when(conditionCaptor.capture());

    // and the captured condition dispatches via the registry
    conditionCaptor.getValue().verifyCondition();
    verify(registry)
        .dispatch(CONDITION_ELEMENT_ACTIVE, processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldRegisterBehaviorWithChainedConditionsAsConjunction() {
    // given - two conditions form an AND-conjunction
    final ConditionalBehaviorInstruction instruction =
        ImmutableConditionalBehaviorInstruction.builder()
            .addConditions(CONDITION_ELEMENT_ACTIVE)
            .addConditions(CONDITION_USER_TASK_CREATED)
            .addActions(ACTION_COMPLETE_USER_TASK)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then - exactly one BehaviorCondition is registered
    verify(processTestContext).when(conditionCaptor.capture());

    // and invoking it dispatches every condition in order
    conditionCaptor.getValue().verifyCondition();
    final InOrder inOrder = Mockito.inOrder(registry);
    inOrder
        .verify(registry)
        .dispatch(CONDITION_ELEMENT_ACTIVE, processTestContext, camundaClient, assertionFacade);
    inOrder
        .verify(registry)
        .dispatch(CONDITION_USER_TASK_CREATED, processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldShortCircuitConjunctionWhenAssertionFails() {
    // given - the first condition's dispatch throws AssertionError
    final ConditionalBehaviorInstruction instruction =
        ImmutableConditionalBehaviorInstruction.builder()
            .addConditions(CONDITION_ELEMENT_ACTIVE)
            .addConditions(CONDITION_USER_TASK_CREATED)
            .addActions(ACTION_COMPLETE_USER_TASK)
            .build();
    final AssertionError firstFailure = new AssertionError("first condition not met");
    Mockito.doThrow(firstFailure)
        .when(registry)
        .dispatch(CONDITION_ELEMENT_ACTIVE, processTestContext, camundaClient, assertionFacade);

    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);
    verify(processTestContext).when(conditionCaptor.capture());

    // when invoking the captured condition
    final BehaviorCondition condition = conditionCaptor.getValue();

    // then - the AssertionError propagates and the second condition is never dispatched
    assertThatThrownBy(condition::verifyCondition).isSameAs(firstFailure);
    verify(registry, never())
        .dispatch(CONDITION_USER_TASK_CREATED, processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldRegisterAllActionsInOrder() {
    // given
    final ConditionalBehaviorInstruction instruction =
        ImmutableConditionalBehaviorInstruction.builder()
            .addConditions(CONDITION_ELEMENT_ACTIVE)
            .addActions(ACTION_COMPLETE_USER_TASK)
            .addActions(ACTION_COMPLETE_JOB)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(builder, times(2)).then(runnableCaptor.capture());

    // and each captured action dispatches its own instruction in order
    final List<Runnable> capturedActions = runnableCaptor.getAllValues();
    capturedActions.get(0).run();
    verify(registry)
        .dispatch(ACTION_COMPLETE_USER_TASK, processTestContext, camundaClient, assertionFacade);

    capturedActions.get(1).run();
    verify(registry)
        .dispatch(ACTION_COMPLETE_JOB, processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldSetNameWhenProvided() {
    // given
    final ConditionalBehaviorInstruction instruction =
        ImmutableConditionalBehaviorInstruction.builder()
            .name("auto-complete-review-task")
            .addConditions(CONDITION_ELEMENT_ACTIVE)
            .addActions(ACTION_COMPLETE_USER_TASK)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(builder).as(eq("auto-complete-review-task"));
  }

  @Test
  void shouldNotCallAsWhenNameAbsent() {
    // given
    final ConditionalBehaviorInstruction instruction =
        ImmutableConditionalBehaviorInstruction.builder()
            .addConditions(CONDITION_ELEMENT_ACTIVE)
            .addActions(ACTION_COMPLETE_USER_TASK)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(builder, never()).as(anyString());
  }

  @Test
  void shouldRejectNonAssertCondition() {
    // given - CREATE_PROCESS_INSTANCE is not an ASSERT_*
    final CreateProcessInstanceInstruction badCondition =
        ImmutableCreateProcessInstanceInstruction.builder()
            .processDefinitionSelector(
                ImmutableProcessDefinitionSelector.builder().processDefinitionId("p").build())
            .build();

    final ConditionalBehaviorInstruction instruction =
        ImmutableConditionalBehaviorInstruction.builder()
            .addConditions(badCondition)
            .addActions(ACTION_COMPLETE_USER_TASK)
            .build();

    // when / then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CONDITIONAL_BEHAVIOR")
        .hasMessageContaining("condition type")
        .hasMessageContaining("ASSERT_DECISION")
        .hasMessageContaining("ASSERT_ELEMENT_INSTANCE")
        .hasMessageContaining("ASSERT_USER_TASK")
        .hasMessageContaining("CREATE_PROCESS_INSTANCE");
  }

  @Test
  void shouldRejectDisallowedAction() {
    // given - ASSERT_PROCESS_INSTANCE is not an allowed action (assertion as action)
    final ConditionalBehaviorInstruction instruction =
        ImmutableConditionalBehaviorInstruction.builder()
            .addConditions(CONDITION_ELEMENT_ACTIVE)
            .addActions(
                ImmutableAssertProcessInstanceInstruction.builder()
                    .processInstanceSelector(
                        ImmutableProcessInstanceSelector.builder().processDefinitionId("p").build())
                    .build())
            .build();

    // when / then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CONDITIONAL_BEHAVIOR")
        .hasMessageContaining("action type")
        .hasMessageContaining("COMPLETE_JOB")
        .hasMessageContaining("COMPLETE_USER_TASK")
        .hasMessageContaining("PUBLISH_MESSAGE")
        .hasMessageContaining("ASSERT_PROCESS_INSTANCE");
  }

  @Test
  void shouldRejectEmptyConditions() {
    // given
    final ConditionalBehaviorInstruction instruction =
        ImmutableConditionalBehaviorInstruction.builder()
            .addActions(ACTION_COMPLETE_USER_TASK)
            .build();

    // when / then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one condition");
  }

  @Test
  void shouldRejectEmptyActions() {
    // given
    final ConditionalBehaviorInstruction instruction =
        ImmutableConditionalBehaviorInstruction.builder()
            .addConditions(CONDITION_ELEMENT_ACTIVE)
            .build();

    // when / then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one action");
  }
}
