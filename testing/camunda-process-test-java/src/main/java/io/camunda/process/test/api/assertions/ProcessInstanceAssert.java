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
package io.camunda.process.test.api.assertions;

import java.util.Map;
import org.assertj.core.api.ThrowingConsumer;

/** The assertion object to verify a process instance. */
public interface ProcessInstanceAssert {

  /**
   * Verifies that the process instance is active. The verification fails if the process instance is
   * completed, terminated, or not created.
   *
   * <p>The assertion waits until the process instance is created.
   *
   * @return the assertion object
   */
  ProcessInstanceAssert isActive();

  /**
   * Verifies that the process instance is completed. The verification fails if the process instance
   * is active, terminated, or not created.
   *
   * <p>The assertion waits until the process instance is ended.
   *
   * @return the assertion object
   */
  ProcessInstanceAssert isCompleted();

  /**
   * Verifies that the process instance is terminated. The verification fails if the process
   * instance is active, completed, or not created.
   *
   * <p>The assertion waits until the process instance is ended.
   *
   * @return the assertion object
   */
  ProcessInstanceAssert isTerminated();

  /**
   * Verifies that the process instance is created (i.e. active, completed, or terminated). The
   * verification fails if the process instance is not created.
   *
   * <p>The assertion waits until the process instance is created.
   *
   * @return the assertion object
   */
  ProcessInstanceAssert isCreated();

  /**
   * Verifies that the given BPMN elements are active. The verification fails if at least one
   * element is completed, terminated, or not entered.
   *
   * <p>The assertion waits until all elements are created.
   *
   * @param elementIds the BPMN element IDs
   * @return the assertion object
   */
  ProcessInstanceAssert hasActiveElements(String... elementIds);

  /**
   * Verifies that the given BPMN elements are active. The verification fails if at least one
   * element is completed, terminated, or not entered.
   *
   * <p>The assertion waits until all elements are created.
   *
   * @param elementSelectors the selectors for the BPMN elements
   * @return the assertion object
   * @see ElementSelectors
   */
  ProcessInstanceAssert hasActiveElements(ElementSelector... elementSelectors);

  /**
   * Verifies that the given BPMN elements are completed. The verification fails if at least one
   * element is active, terminated, or not entered.
   *
   * <p>The assertion waits until all elements are left.
   *
   * @param elementIds the BPMN element IDs
   * @return the assertion object
   */
  ProcessInstanceAssert hasCompletedElements(String... elementIds);

  /**
   * Verifies that the given BPMN elements are completed. The verification fails if at least one
   * element is active, terminated, or not entered.
   *
   * <p>The assertion waits until all elements are left.
   *
   * @param elementSelectors the selectors for the BPMN elements
   * @return the assertion object
   * @see ElementSelectors
   */
  ProcessInstanceAssert hasCompletedElements(ElementSelector... elementSelectors);

  /**
   * Verifies that the given BPMN elements are completed in order. Elements that do not match any of
   * the given element IDs are ignored.
   *
   * <p>The verification fails if at least one of the elements is not completed, or the order is not
   * correct.
   *
   * <p>The assertion waits until all elements are left.
   *
   * @param elementIds the BPMN element IDs
   * @return the assertion object
   */
  ProcessInstanceAssert hasCompletedElementsInOrder(String... elementIds);

  /**
   * Verifies that the given BPMN elements are completed in order. Elements that do not match any of
   * the given element selectors are ignored.
   *
   * <p>The verification fails if at least one of the elements is not completed, or the order is not
   * correct.
   *
   * <p>The assertion waits until all elements are left.
   *
   * @param elementSelectors the selectors for the BPMN elements
   * @return the assertion object
   */
  ProcessInstanceAssert hasCompletedElementsInOrder(ElementSelector... elementSelectors);

  /**
   * Verifies that the given BPMN elements are terminated. The verification fails if at least one
   * element is active, completed, or not entered.
   *
   * <p>The assertion waits until all elements are left.
   *
   * @param elementIds the BPMN element IDs
   * @return the assertion object
   */
  ProcessInstanceAssert hasTerminatedElements(String... elementIds);

  /**
   * Verifies that the given BPMN elements are terminated. The verification fails if at least one
   * element is active, completed, or not entered.
   *
   * <p>The assertion waits until all elements are left.
   *
   * @param elementSelectors the selectors for the BPMN elements
   * @return the assertion object
   * @see ElementSelectors
   */
  ProcessInstanceAssert hasTerminatedElements(ElementSelector... elementSelectors);

  /**
   * Verifies that the BPMN element is active the given amount of times. The verification fails if
   * the element is not active or not exactly the given amount of times.
   *
   * <p>The assertion waits until the element is active the given amount of times.
   *
   * @param elementId the BPMN element ID
   * @param times the expected amount of times
   * @return the assertion object
   */
  ProcessInstanceAssert hasActiveElement(String elementId, int times);

  /**
   * Verifies that the BPMN element is active the given amount of times. The verification fails if
   * the element is not active or not exactly the given amount of times.
   *
   * <p>The assertion waits until the element is active the given amount of times.
   *
   * @param elementSelector the selectors for the BPMN element
   * @param times the expected amount of times
   * @return the assertion object
   */
  ProcessInstanceAssert hasActiveElement(ElementSelector elementSelector, int times);

  /**
   * Verifies that the BPMN element is completed the given amount of times. The verification fails
   * if the element is not completed or not exactly the given amount of times.
   *
   * <p>The assertion waits until the element is completed the given amount of times.
   *
   * @param elementId the BPMN element ID
   * @param times the expected amount of times
   * @return the assertion object
   */
  ProcessInstanceAssert hasCompletedElement(String elementId, int times);

  /**
   * Verifies that the BPMN element is completed the given amount of times. The verification fails
   * if the element is not completed or not exactly the given amount of times.
   *
   * <p>The assertion waits until the element is completed the given amount of times.
   *
   * @param elementSelector the selectors for the BPMN element
   * @param times the expected amount of times
   * @return the assertion object
   */
  ProcessInstanceAssert hasCompletedElement(ElementSelector elementSelector, int times);

  /**
   * Verifies that the BPMN element is terminated the given amount of times. The verification fails
   * if the element is not terminated or not exactly the given amount of times.
   *
   * <p>The assertion waits until the element is terminated the given amount of times.
   *
   * @param elementId the BPMN element ID
   * @param times the expected amount of times
   * @return the assertion object
   */
  ProcessInstanceAssert hasTerminatedElement(String elementId, int times);

  /**
   * Verifies that the BPMN element is terminated the given amount of times. The verification fails
   * if the element is not terminated or not exactly the given amount of times.
   *
   * <p>The assertion waits until the element is terminated the given amount of times.
   *
   * @param elementSelector the selectors for the BPMN element
   * @param times the expected amount of times
   * @return the assertion object
   */
  ProcessInstanceAssert hasTerminatedElement(ElementSelector elementSelector, int times);

  /**
   * Verifies that the given BPMN elements are not activated (i.e. not entered). The verification
   * fails if at least one element is active, completed, or terminated.
   *
   * <p>The assertion doesn't wait for the given activities.
   *
   * @param elementIds the BPMN element IDs
   * @return the assertion object
   */
  ProcessInstanceAssert hasNotActivatedElements(String... elementIds);

  /**
   * Verifies that the given BPMN elements are not activated (i.e. not entered). The verification
   * fails if at least one element is active, completed, or terminated.
   *
   * <p>The assertion doesn't wait for the given activities.
   *
   * @param elementSelectors the selectors for the BPMN elements
   * @return the assertion object
   * @see ElementSelectors
   */
  ProcessInstanceAssert hasNotActivatedElements(ElementSelector... elementSelectors);

  /**
   * Verifies that the given BPMN elements are not active. The verification fails if at least one
   * element is active.
   *
   * <p>The assertion waits until the elements are completed or terminated, if they are active.
   *
   * @param elementIds the BPMN element IDs
   * @return the assertion object
   */
  ProcessInstanceAssert hasNoActiveElements(String... elementIds);

  /**
   * Verifies that the given BPMN elements are not active. The verification fails if at least one
   * element is active.
   *
   * <p>The assertion waits until the elements are completed or terminated, if they are active.
   *
   * @param elementSelectors the selectors for the BPMN elements
   * @return the assertion object
   * @see ElementSelectors
   */
  ProcessInstanceAssert hasNoActiveElements(ElementSelector... elementSelectors);

  /**
   * Verifies that only the given BPMN elements are active. The verification fails if at least one
   * element is not active, or other elements are active.
   *
   * <p>The assertion waits until only the given elements are active.
   *
   * @param elementIds the BPMN element IDs
   * @return the assertion object
   */
  ProcessInstanceAssert hasActiveElementsExactly(String... elementIds);

  /**
   * Verifies that only the given BPMN elements are active. The verification fails if at least one
   * element is not active, or other elements are active.
   *
   * <p>The assertion waits until only the given elements are active.
   *
   * @param elementSelectors the selectors for the BPMN elements
   * @return the assertion object
   * @see ElementSelectors
   */
  ProcessInstanceAssert hasActiveElementsExactly(ElementSelector... elementSelectors);

  /**
   * Verifies that the process instance has the given variables. The verification fails if at least
   * one variable doesn't exist.
   *
   * <p>The assertion waits until all variables exist.
   *
   * @param variableNames the variable names
   * @return the assertion object
   */
  ProcessInstanceAssert hasVariableNames(String... variableNames);

  /**
   * Verifies that the given BPMN element has the specified local variables. Local variables are
   * scoped to the element and do not appear in the parent scope. The verification fails if at least
   * one variable doesn't exist.
   *
   * <p>The assertion waits until all local variables exist for the element.
   *
   * @param elementId the id of the BPMN element
   * @param variableNames the variable names
   * @return the assertion object
   */
  ProcessInstanceAssert hasLocalVariableNames(String elementId, String... variableNames);

  /**
   * Verifies that the given BPMN element has the specified local variables. Local variables are
   * scoped to the element and do not appear in the parent scope. The verification fails if at least
   * one variable doesn't exist.
   *
   * <p>The assertion waits until all local variables exist for the element.
   *
   * @param selector the selector for the BPMN element
   * @param variableNames the variable names
   * @return the assertion object
   * @see ElementSelectors
   */
  ProcessInstanceAssert hasLocalVariableNames(ElementSelector selector, String... variableNames);

  /**
   * Verifies that the process instance has the variable with the given value. The verification
   * fails if the variable doesn't exist or has a different value.
   *
   * <p>The assertion waits until the variable exists and has the given value.
   *
   * @param variableName the variable name
   * @param variableValue the variable value
   * @return the assertion object
   */
  ProcessInstanceAssert hasVariable(String variableName, Object variableValue);

  /**
   * Verifies that the given BPMN element has the local variable with the specified value. Local
   * variables are scoped to the element and do not appear in the parent scope. The verification
   * fails if the variable doesn't exist or has a different value.
   *
   * <p>The assertion waits until the variable exists and has the given value.
   *
   * @param elementId the id of the BPMN element
   * @param variableName the variable name
   * @param variableValue the variable value
   * @return the assertion object
   */
  ProcessInstanceAssert hasLocalVariable(
      String elementId, String variableName, Object variableValue);

  /**
   * Verifies that the given BPMN element has the local variable with the specified value. Local
   * variables are scoped to the element and do not appear in the parent scope. The verification
   * fails if the variable doesn't exist or has a different value.
   *
   * <p>The assertion waits until the variable exists and has the given value.
   *
   * @param selector the selector for the BPMN element
   * @param variableName the variable name
   * @param variableValue the variable value
   * @return the assertion object
   * @see ElementSelectors
   */
  ProcessInstanceAssert hasLocalVariable(
      ElementSelector selector, String variableName, Object variableValue);

  /**
   * Verifies that the process instance has a variable with a value that satisfies the given
   * requirements expressed as a {@link ThrowingConsumer}. It can be used to verify a complex object
   * partially with multiple grouped assertions. The verification fails if the variable doesn't
   * exist or the value doesn't satisfy the requirements.
   *
   * <p>The assertion waits until the variable exists and the value satisfies the requirements.
   *
   * @param variableName the variable name
   * @param variableValueType the variable value's deserialization type, can be a JsonNode or Map
   *     for a generic variable
   * @param requirement the requirement that the variable must satisfy
   * @return the assertion object
   */
  <T> ProcessInstanceAssert hasVariableSatisfies(
      String variableName, final Class<T> variableValueType, final ThrowingConsumer<T> requirement);

  /**
   * Verifies that the process instance has a local variable with a value that satisfies the given
   * requirements expressed as a {@link ThrowingConsumer}. It can be used to verify a complex object
   * partially with multiple grouped assertions. The verification fails if the variable doesn't
   * exist or the value doesn't satisfy the requirements.
   *
   * <p>The assertion waits until the variable exists and the value satisfies the requirements.
   *
   * @param elementId id of the element the local variable is associated with
   * @param variableName the variable name
   * @param variableValueType the variable value's deserialization type, can be a JsonNode or Map
   *     for a generic variable
   * @param requirement the requirement that the variable must satisfy
   * @return the assertion object
   */
  <T> ProcessInstanceAssert hasLocalVariableSatisfies(
      String elementId,
      String variableName,
      Class<T> variableValueType,
      ThrowingConsumer<T> requirement);

  /**
   * Verifies that the process instance has a local variable with a value that satisfies the given
   * requirements expressed as a {@link ThrowingConsumer}. It can be used to verify a complex object
   * partially with multiple grouped assertions. The verification fails if the variable doesn't
   * exist or the value doesn't satisfy the requirements.
   *
   * <p>The assertion waits until the variable exists and the value satisfies the requirements.
   *
   * @param selector the {@see ElementSelector} for the BPMN element the variable is associated with
   * @param variableName the variable name
   * @param variableValueType the variable value's deserialization type, can be a JsonNode or Map
   *     for a generic variable
   * @param requirement the requirement that the variable must satisfy
   * @return the assertion object
   */
  <T> ProcessInstanceAssert hasLocalVariableSatisfies(
      ElementSelector selector,
      String variableName,
      Class<T> variableValueType,
      ThrowingConsumer<T> requirement);

  /**
   * Verifies that the process instance has the given variables. The verification fails if at least
   * one variable doesn't exist or has a different value.
   *
   * <p>The assertion waits until all variables exist and have the given value.
   *
   * @param variables the expected variables
   * @return the assertion object
   */
  ProcessInstanceAssert hasVariables(Map<String, Object> variables);

  /**
   * Verifies that the given element associated with the process instance has the given local
   * variables. Local variables are scoped to the element and do not appear in the parent scope. The
   * verification fails if at least one variable doesn't exist or has a different value.
   *
   * <p>The assertion waits until all variables exist and have the given values.
   *
   * @param elementId the id of the BPMN element
   * @param variables the expected variables
   * @return the assertion object
   */
  ProcessInstanceAssert hasLocalVariables(String elementId, Map<String, Object> variables);

  /**
   * Verifies that the given element associated with the process instance has the given local
   * variables. Local variables are scoped to the element and do not appear in the parent scope. The
   * verification fails if at least one variable doesn't exist or has a different value.
   *
   * <p>The assertion waits until all variables exist and have the given values.
   *
   * @param selector the selector for the BPMN element
   * @param variables the expected variables
   * @return the assertion object
   * @see ElementSelectors
   */
  ProcessInstanceAssert hasLocalVariables(ElementSelector selector, Map<String, Object> variables);

  /**
   * Verifies that the process instance has no active incidents. The verification fails if there is
   * any active incident.
   *
   * <p>The assertion waits until all active incidents are resolved.
   *
   * @return the assertion object
   */
  ProcessInstanceAssert hasNoActiveIncidents();

  /**
   * Verifies that the process instance has at least one active incident. The verification fails if
   * there is no active incident.
   *
   * <p>The assertion waits until there is an active incident.
   *
   * @return the assertion object
   */
  ProcessInstanceAssert hasActiveIncidents();

  /**
   * Verifies that the process instance is currently waiting to receive one or more specified
   * messages.
   *
   * <p>The assertion waits for the correct message subscription.
   *
   * @param expectedMessageName the name of the message
   * @return the assertion object
   */
  ProcessInstanceAssert isWaitingForMessage(final String expectedMessageName);

  /**
   * Verifies that the process instance is currently waiting to receive one or more specified
   * messages.
   *
   * <p>The assertion waits for the correct message subscription.
   *
   * @param expectedMessageName the name of the message
   * @param correlationKey the message's correlation key
   * @return the assertion object
   */
  ProcessInstanceAssert isWaitingForMessage(
      final String expectedMessageName, final String correlationKey);

  /**
   * Verifies that the process instance is not currently waiting to receive one or more specified
   * messages.
   *
   * <p>The assertion waits for a message subscription that may invalidate the assertion
   *
   * @param expectedMessageName the name of the message
   * @return the assertion object
   */
  ProcessInstanceAssert isNotWaitingForMessage(final String expectedMessageName);

  /**
   * Verifies that the process instance is not currently waiting to receive one or more specified
   * messages.
   *
   * <p>The assertion waits for a message subscription that may invalidate the assertion
   *
   * @param expectedMessageName the name of the message
   * @param correlationKey the message's correlation key
   * @return the assertion object
   */
  ProcessInstanceAssert isNotWaitingForMessage(
      final String expectedMessageName, final String correlationKey);
}
