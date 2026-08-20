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
package io.camunda.process.test.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.process.test.api.assertions.DecisionSelectors;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.utils.DecisionInstanceBuilder;
import io.camunda.process.test.utils.ElementInstanceBuilder;
import io.camunda.process.test.utils.MessageSubscriptionBuilder;
import io.camunda.process.test.utils.ProcessInstanceBuilder;
import io.camunda.process.test.utils.UserTaskBuilder;
import io.camunda.process.test.utils.VariableBuilder;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AssertionConfigurationTest {

  private static final Duration ASSERTION_TIMEOUT_OVERRIDE = Duration.ofMinutes(1);

  @Spy
  private CamundaAssertAwaitBehavior globalAwaitBehavior = CamundaAssert.DEFAULT_AWAIT_BEHAVIOR;

  @Spy
  private CamundaAssertAwaitBehavior overrideAwaitBehavior = CamundaAssert.DEFAULT_AWAIT_BEHAVIOR;

  @Mock private CamundaDataSource camundaDataSource;

  @BeforeEach
  void configureMocks() {
    CamundaAssert.initialize(camundaDataSource);

    CamundaAssert.setAwaitBehavior(globalAwaitBehavior);

    lenient()
        .when(globalAwaitBehavior.withAssertionTimeout(any()))
        .thenReturn(overrideAwaitBehavior);
  }

  @AfterEach
  void resetAssertion() {
    CamundaAssert.setAwaitBehavior(CamundaAssert.DEFAULT_AWAIT_BEHAVIOR);
  }

  @Nested
  class ProcessAssertionTests {

    private static final long PROCESS_INSTANCE_KEY = 100L;

    @BeforeEach
    void configureDataSource() {
      // given
      lenient()
          .when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      lenient()
          .when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ElementInstanceBuilder.newActiveElementInstance(
                          "fly-space-shuttle", PROCESS_INSTANCE_KEY)
                      .build()));

      lenient()
          .when(camundaDataSource.findVariables(any()))
          .thenReturn(
              Collections.singletonList(
                  VariableBuilder.newVariable("location", "\"moon\"").build()));

      lenient()
          .when(camundaDataSource.findMessageSubscriptions(any()))
          .thenReturn(
              Collections.singletonList(
                  MessageSubscriptionBuilder.newActiveMessageSubscription(
                          "landing permitted", "Artemis")
                      .build()));
    }

    @Test
    void shouldUseGlobalTimeout() {
      // when
      CamundaAssert.assertThatProcessInstance(ProcessInstanceSelectors.byKey(PROCESS_INSTANCE_KEY))
          .isActive();

      // then
      verify(globalAwaitBehavior).untilAsserted(any());
      verifyNoInteractions(overrideAwaitBehavior);
    }

    @Test
    void shouldOverrideTimeout() {
      // when
      CamundaAssert.assertThatProcessInstance(ProcessInstanceSelectors.byKey(PROCESS_INSTANCE_KEY))
          .withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE)
          .isActive();

      // then
      verify(globalAwaitBehavior).withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE);
      verify(overrideAwaitBehavior).untilAsserted(any());
      verifyNoMoreInteractions(globalAwaitBehavior);
    }

    @Test
    void shouldOverrideTimeoutForCompleteChain() {
      // when
      CamundaAssert.assertThatProcessInstance(ProcessInstanceSelectors.byKey(PROCESS_INSTANCE_KEY))
          .withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE)
          .isActive()
          .hasActiveElements("fly-space-shuttle")
          .hasVariable("location", "moon")
          .isWaitingForMessage("landing permitted")
          .hasNoActiveIncidents();

      // then
      verify(globalAwaitBehavior).withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE);
      verify(overrideAwaitBehavior, times(5)).untilAsserted(any());
      verifyNoMoreInteractions(globalAwaitBehavior);
    }

    @Test
    void shouldOverrideTimeoutInChain() {
      // when
      CamundaAssert.assertThatProcessInstance(ProcessInstanceSelectors.byKey(PROCESS_INSTANCE_KEY))
          .isActive()
          .hasActiveElements("fly-space-shuttle")
          .withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE)
          .hasVariable("location", "moon")
          .isWaitingForMessage("landing permitted")
          .hasNoActiveIncidents();

      // then
      final InOrder inOrder = inOrder(globalAwaitBehavior, overrideAwaitBehavior);
      inOrder.verify(globalAwaitBehavior, times(2)).untilAsserted(any());
      inOrder.verify(overrideAwaitBehavior, times(3)).untilAsserted(any());
    }

    @Test
    void shouldNotOverrideGlobalTimeout() {
      // when
      CamundaAssert.assertThatProcessInstance(ProcessInstanceSelectors.byKey(PROCESS_INSTANCE_KEY))
          .withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE)
          .isActive();

      CamundaAssert.assertThatProcessInstance(ProcessInstanceSelectors.byKey(PROCESS_INSTANCE_KEY))
          .isActive();

      // then
      final InOrder inOrder = inOrder(globalAwaitBehavior, overrideAwaitBehavior);
      inOrder.verify(overrideAwaitBehavior, times(1)).untilAsserted(any());
      inOrder.verify(globalAwaitBehavior, times(1)).untilAsserted(any());
    }
  }

  @Nested
  class DecisionInstanceAssertionTests {

    private static final long DECISION_INSTANCE_KEY = 200L;
    private static final String DECISION_INSTANCE_ID = "200";
    private static final String DECISION_ID = "decide-astronaut";

    @BeforeEach
    void configureDataSource() {
      // given
      final DecisionInstance decisionInstance =
          DecisionInstanceBuilder.newEvaluatedDecisionInstance(DECISION_INSTANCE_KEY)
              .setDecisionInstanceId(DECISION_INSTANCE_ID)
              .setDecisionDefinitionId(DECISION_ID)
              .setResult("\"Zee\"")
              .build();

      lenient()
          .when(camundaDataSource.findDecisionInstances(any()))
          .thenReturn(Collections.singletonList(decisionInstance));

      lenient()
          .when(camundaDataSource.getDecisionInstance(DECISION_INSTANCE_ID))
          .thenReturn(decisionInstance);
    }

    @Test
    void shouldUseGlobalTimeout() {
      // when
      CamundaAssert.assertThatDecision(DecisionSelectors.byId(DECISION_ID)).isEvaluated();

      // then
      verify(globalAwaitBehavior).untilAsserted(any());
      verifyNoInteractions(overrideAwaitBehavior);
    }

    @Test
    void shouldOverrideTimeout() {
      // when
      CamundaAssert.assertThatDecision(DecisionSelectors.byId(DECISION_ID))
          .withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE)
          .isEvaluated();

      // then
      verify(globalAwaitBehavior).withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE);
      verify(overrideAwaitBehavior).untilAsserted(any());
      verifyNoMoreInteractions(globalAwaitBehavior);
    }

    @Test
    void shouldOverrideTimeoutForCompleteChain() {
      // when
      CamundaAssert.assertThatDecision(DecisionSelectors.byId(DECISION_ID))
          .withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE)
          .isEvaluated()
          .hasOutput("Zee");

      // then
      verify(globalAwaitBehavior).withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE);
      verify(overrideAwaitBehavior, times(2)).untilAsserted(any());
      verifyNoMoreInteractions(globalAwaitBehavior);
    }

    @Test
    void shouldOverrideTimeoutInChain() {
      // when
      CamundaAssert.assertThatDecision(DecisionSelectors.byId(DECISION_ID))
          .isEvaluated()
          .withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE)
          .hasOutput("Zee");

      // then
      final InOrder inOrder = inOrder(globalAwaitBehavior, overrideAwaitBehavior);
      inOrder.verify(globalAwaitBehavior, times(1)).untilAsserted(any());
      inOrder.verify(overrideAwaitBehavior, times(1)).untilAsserted(any());
    }

    @Test
    void shouldNotOverrideGlobalTimeout() {
      // when
      CamundaAssert.assertThatDecision(DecisionSelectors.byId(DECISION_ID))
          .withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE)
          .isEvaluated();

      CamundaAssert.assertThatDecision(DecisionSelectors.byId(DECISION_ID)).isEvaluated();

      // then
      final InOrder inOrder = inOrder(globalAwaitBehavior, overrideAwaitBehavior);
      inOrder.verify(overrideAwaitBehavior, times(1)).untilAsserted(any());
      inOrder.verify(globalAwaitBehavior, times(1)).untilAsserted(any());
    }
  }

  @Nested
  class UserTaskAssertionTests {

    private static final long USER_TASK_KEY = 300L;
    private static final String ELEMENT_ID = "perform-astronaut-training";

    @BeforeEach
    void configureDataSource() {
      // given
      lenient()
          .when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  UserTaskBuilder.newCreatedUserTask(USER_TASK_KEY)
                      .setElementId(ELEMENT_ID)
                      .setAssignee("Zee")
                      .build()));
    }

    @Test
    void shouldUseGlobalTimeout() {
      // when
      CamundaAssert.assertThatUserTask(UserTaskSelectors.byElementId(ELEMENT_ID)).isCreated();

      // then
      verify(globalAwaitBehavior).untilAsserted(any());
      verifyNoInteractions(overrideAwaitBehavior);
    }

    @Test
    void shouldOverrideTimeout() {
      // when
      CamundaAssert.assertThatUserTask(UserTaskSelectors.byElementId(ELEMENT_ID))
          .withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE)
          .isCreated();

      // then
      verify(globalAwaitBehavior).withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE);
      verify(overrideAwaitBehavior).untilAsserted(any());
      verifyNoMoreInteractions(globalAwaitBehavior);
    }

    @Test
    void shouldOverrideTimeoutForCompleteChain() {
      // when
      CamundaAssert.assertThatUserTask(UserTaskSelectors.byElementId(ELEMENT_ID))
          .withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE)
          .isCreated()
          .hasAssignee("Zee");

      // then
      verify(globalAwaitBehavior).withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE);
      verify(overrideAwaitBehavior, times(2)).untilAsserted(any());
      verifyNoMoreInteractions(globalAwaitBehavior);
    }

    @Test
    void shouldOverrideTimeoutInChain() {
      // when
      CamundaAssert.assertThatUserTask(UserTaskSelectors.byElementId(ELEMENT_ID))
          .isCreated()
          .withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE)
          .hasAssignee("Zee");

      // then
      final InOrder inOrder = inOrder(globalAwaitBehavior, overrideAwaitBehavior);
      inOrder.verify(globalAwaitBehavior, times(1)).untilAsserted(any());
      inOrder.verify(overrideAwaitBehavior, times(1)).untilAsserted(any());
    }

    @Test
    void shouldNotOverrideGlobalTimeout() {
      // when
      CamundaAssert.assertThatUserTask(UserTaskSelectors.byElementId(ELEMENT_ID))
          .withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE)
          .isCreated();

      CamundaAssert.assertThatUserTask(UserTaskSelectors.byElementId(ELEMENT_ID)).isCreated();

      // then
      final InOrder inOrder = inOrder(globalAwaitBehavior, overrideAwaitBehavior);
      inOrder.verify(overrideAwaitBehavior, times(1)).untilAsserted(any());
      inOrder.verify(globalAwaitBehavior, times(1)).untilAsserted(any());
    }
  }

  @Nested
  class AwaitBehaviorTests {

    @Test
    void shouldCreateInstanceWithTimeout() {
      // given
      final CamundaAssertAwaitBehavior awaitBehavior = CamundaAssert.DEFAULT_AWAIT_BEHAVIOR;

      // when
      final CamundaAssertAwaitBehavior awaitBehaviorWithOverride =
          awaitBehavior.withAssertionTimeout(ASSERTION_TIMEOUT_OVERRIDE);

      // then
      assertThat(awaitBehaviorWithOverride.getAssertionTimeout())
          .isEqualTo(ASSERTION_TIMEOUT_OVERRIDE);
      assertThat(awaitBehaviorWithOverride.getAssertionInterval())
          .isEqualTo(awaitBehavior.getAssertionInterval());

      assertThat(awaitBehavior.getAssertionTimeout())
          .isEqualTo(CamundaAssert.DEFAULT_ASSERTION_TIMEOUT);
      assertThat(awaitBehavior.getAssertionInterval())
          .isEqualTo(CamundaAssert.DEFAULT_ASSERTION_INTERVAL);
    }
  }
}
