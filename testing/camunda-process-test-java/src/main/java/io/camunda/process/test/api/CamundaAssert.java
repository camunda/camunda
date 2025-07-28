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

import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.process.test.api.assertions.DecisionInstanceAssert;
import io.camunda.process.test.api.assertions.DecisionSelector;
import io.camunda.process.test.api.assertions.DecisionSelectors;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.ElementSelectors;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.assertions.UserTaskAssert;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.assertions.DecisionInstanceAssertj;
import io.camunda.process.test.impl.assertions.ProcessInstanceAssertj;
import io.camunda.process.test.impl.assertions.UserTaskAssertj;
import io.camunda.process.test.impl.assertions.util.AwaitilityBehavior;
import java.time.Duration;
import java.util.function.Function;

/**
 * The entry point for all assertions.
 *
 * <p>Example usage:
 *
 * <pre>
 *   &#064;Test
 *   void shouldWork() {
 *     // given
 *     final ProcessInstanceEvent processInstance =
 *         zeebeClient
 *             .newCreateInstanceCommand()
 *             .bpmnProcessId("process")
 *             .latestVersion()
 *             .send()
 *             .join();
 *
 *     // when
 *
 *     // then
 *     CamundaAssert.assertThat(processInstance)
 *         .isCompleted()
 *         .hasCompletedElements("A", "B");
 *   }
 * }
 * </pre>
 */
public class CamundaAssert {

  /** The default time how long an assertion waits until the expected state is reached. */
  public static final Duration DEFAULT_ASSERTION_TIMEOUT = Duration.ofSeconds(10);

  /** The default time between two assertion attempts until the expected state is reached. */
  public static final Duration DEFAULT_ASSERTION_INTERVAL = Duration.ofMillis(100);

  /** The default element selector used for BPMN element assertions with a string identifier. */
  public static final Function<String, ElementSelector> DEFAULT_ELEMENT_SELECTOR =
      ElementSelectors::byId;

  /** The default await behavior for the assertions. */
  public static final CamundaAssertAwaitBehavior DEFAULT_AWAIT_BEHAVIOR = new AwaitilityBehavior();

  private static final ThreadLocal<CamundaDataSource> DATA_SOURCE = new ThreadLocal<>();

  private static Function<String, ElementSelector> elementSelector = DEFAULT_ELEMENT_SELECTOR;

  private static CamundaAssertAwaitBehavior awaitBehavior = DEFAULT_AWAIT_BEHAVIOR;

  static {
    setAssertionTimeout(DEFAULT_ASSERTION_TIMEOUT);
    setAssertionInterval(DEFAULT_ASSERTION_INTERVAL);
  }

  // ======== Configuration options ========

  /**
   * Configures the time how long an assertion waits until the expected state is reached.
   *
   * @param assertionTimeout the maximum time of an assertion
   * @see #DEFAULT_ASSERTION_TIMEOUT
   */
  public static void setAssertionTimeout(final Duration assertionTimeout) {
    awaitBehavior.setAssertionTimeout(assertionTimeout);
  }

  /**
   * Configures the time between two assertion attempts until the expected state is reached.
   *
   * @param assertionInterval time between two assertion attempts
   * @see #DEFAULT_ASSERTION_INTERVAL
   */
  public static void setAssertionInterval(final Duration assertionInterval) {
    awaitBehavior.setAssertionInterval(assertionInterval);
  }

  /**
   * Configures the element selector used for BPMN element assertions with a string identifier.
   *
   * @param elementSelector the element selector to use
   * @see #DEFAULT_ELEMENT_SELECTOR
   */
  public static void setElementSelector(final Function<String, ElementSelector> elementSelector) {
    CamundaAssert.elementSelector = elementSelector;
  }

  static CamundaAssertAwaitBehavior getAwaitBehavior() {
    return awaitBehavior;
  }

  /**
   * Configures the await behavior for the assertions.
   *
   * @param awaitBehavior the behavior to use for waiting
   * @see #DEFAULT_AWAIT_BEHAVIOR
   */
  public static void setAwaitBehavior(final CamundaAssertAwaitBehavior awaitBehavior) {
    CamundaAssert.awaitBehavior = awaitBehavior;
  }

  // ======== Assertions ========

  /**
   * To verify a process instance.
   *
   * @param processInstanceEvent the event of the process instance to verify
   * @return the assertion object
   */
  public static ProcessInstanceAssert assertThatProcessInstance(
      final ProcessInstanceEvent processInstanceEvent) {
    return createProcessInstanceAssertj(processInstanceEvent.getProcessInstanceKey());
  }

  /**
   * @deprecated, for removal, use {@link #assertThatProcessInstance(ProcessInstanceEvent)} instead
   */
  @Deprecated
  public static ProcessInstanceAssert assertThatProcessInstance(
      final io.camunda.zeebe.client.api.response.ProcessInstanceEvent processInstanceEvent) {
    return createProcessInstanceAssertj(processInstanceEvent.getProcessInstanceKey());
  }

  /**
   * To verify a process instance.
   *
   * @param processInstanceEvent the event of the process instance to verify
   * @return the assertion object
   */
  public static ProcessInstanceAssert assertThat(final ProcessInstanceEvent processInstanceEvent) {
    return assertThatProcessInstance(processInstanceEvent);
  }

  /**
   * @deprecated, for removal, use {@link #assertThatProcessInstance(ProcessInstanceEvent)} instead
   */
  @Deprecated
  public static ProcessInstanceAssert assertThat(
      final io.camunda.zeebe.client.api.response.ProcessInstanceEvent processInstanceEvent) {
    return assertThatProcessInstance(processInstanceEvent);
  }

  /**
   * To verify a process instance.
   *
   * @param processInstanceResult the result of the process instance to verify
   * @return the assertion object
   */
  public static ProcessInstanceAssert assertThatProcessInstance(
      final ProcessInstanceResult processInstanceResult) {
    return createProcessInstanceAssertj(processInstanceResult.getProcessInstanceKey());
  }

  /**
   * @deprecated, for removal, use {@link #assertThatProcessInstance(ProcessInstanceResult)} instead
   */
  @Deprecated
  public static ProcessInstanceAssert assertThatProcessInstance(
      final io.camunda.zeebe.client.api.response.ProcessInstanceResult processInstanceResult) {
    return createProcessInstanceAssertj(processInstanceResult.getProcessInstanceKey());
  }

  /**
   * To verify a process instance.
   *
   * @param processInstanceResult the result of the process instance to verify
   * @return the assertion object
   */
  public static ProcessInstanceAssert assertThat(
      final ProcessInstanceResult processInstanceResult) {
    return assertThatProcessInstance(processInstanceResult);
  }

  /**
   * @deprecated, for removal, use {@link #assertThat(ProcessInstanceEvent)} instead
   */
  @Deprecated
  public static ProcessInstanceAssert assertThat(
      final io.camunda.zeebe.client.api.response.ProcessInstanceResult processInstanceResult) {
    return assertThatProcessInstance(processInstanceResult);
  }

  /**
   * To verify a process instance.
   *
   * @param processInstanceSelector the selector of the process instance to verify
   * @return the assertion object
   * @see io.camunda.process.test.api.assertions.ProcessInstanceSelectors
   */
  public static ProcessInstanceAssert assertThatProcessInstance(
      final ProcessInstanceSelector processInstanceSelector) {
    return new ProcessInstanceAssertj(
        getDataSource(), awaitBehavior, processInstanceSelector, elementSelector);
  }

  /**
   * To verify a process instance.
   *
   * @param processInstanceSelector the selector of the process instance to verify
   * @return the assertion object
   * @see io.camunda.process.test.api.assertions.ProcessInstanceSelectors
   */
  public static ProcessInstanceAssert assertThat(
      final ProcessInstanceSelector processInstanceSelector) {
    return assertThatProcessInstance(processInstanceSelector);
  }

  private static ProcessInstanceAssertj createProcessInstanceAssertj(
      final long processInstanceKey) {
    return new ProcessInstanceAssertj(
        getDataSource(), awaitBehavior, processInstanceKey, elementSelector);
  }

  /**
   * To verify a user task.
   *
   * @param userTaskSelector the selector of the user task to verify
   * @return the assertion object
   * @see io.camunda.process.test.api.assertions.UserTaskSelectors
   */
  public static UserTaskAssert assertThatUserTask(final UserTaskSelector userTaskSelector) {
    return new UserTaskAssertj(getDataSource(), awaitBehavior, userTaskSelector);
  }

  /**
   * To verify a user task.
   *
   * @param userTaskSelector the selector of the user task to verify
   * @return the assertion object
   * @see io.camunda.process.test.api.assertions.UserTaskSelectors
   */
  public static UserTaskAssert assertThat(final UserTaskSelector userTaskSelector) {
    return assertThatUserTask(userTaskSelector);
  }

  /**
   * To verify a decision instance (via business rule task).
   *
   * @param decisionSelector the selector of the decision instance to verify
   * @return the assertion object
   * @see io.camunda.process.test.api.assertions.DecisionSelectors
   */
  public static DecisionInstanceAssert assertThatDecision(final DecisionSelector decisionSelector) {
    return new DecisionInstanceAssertj(getDataSource(), awaitBehavior, decisionSelector);
  }

  /**
   * To verify a decision instance (via business rule task).
   *
   * @param decisionSelector the selector of the decision instance to verify
   * @return the assertion object
   * @see io.camunda.process.test.api.assertions.DecisionSelectors
   */
  public static DecisionInstanceAssert assertThat(final DecisionSelector decisionSelector) {
    return assertThatDecision(decisionSelector);
  }

  /**
   * To verify an evaluated decision response (via API).
   *
   * @param response the evaluated decision response to assert
   * @return the assertion object
   */
  public static DecisionInstanceAssertj assertThatDecision(
      final EvaluateDecisionResponse response) {
    return new DecisionInstanceAssertj(
        getDataSource(), awaitBehavior, DecisionSelectors.byResponse(response));
  }

  /**
   * To verify an evaluated decision response (via API).
   *
   * @param response the evaluated decision response to assert
   * @return the assertion object
   */
  public static DecisionInstanceAssertj assertThat(final EvaluateDecisionResponse response) {
    return assertThatDecision(response);
  }

  // ======== Internal ========

  private static CamundaDataSource getDataSource() {
    if (DATA_SOURCE.get() == null) {
      throw new IllegalStateException(
          "No data source is set. Maybe you run outside of a testcase?");
    }
    return DATA_SOURCE.get();
  }

  /**
   * Initializes the assertions by setting the data source. Must be called before the assertions are
   * used.
   */
  static void initialize(final CamundaDataSource dataSource) {
    CamundaAssert.DATA_SOURCE.set(dataSource);
  }

  /**
   * Resets the assertions by removing the data source. Must call {@link
   * #initialize(CamundaDataSource)} before the assertions can be used again.
   */
  static void reset() {
    CamundaAssert.DATA_SOURCE.remove();
  }
}
