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
   * Verifies that the process instance has the given variables. The verification fails if at least
   * one variable doesn't exist or has a different value.
   *
   * <p>The assertion waits until all variables exist and have the given value.
   *
   * @param variables the expected variables
   * @return the assertion object
   */
  ProcessInstanceAssert hasVariables(Map<String, Object> variables);
}
