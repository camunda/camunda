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

import io.camunda.client.api.search.enums.IncidentErrorType;

/** The assertion object to verify an incident. */
public interface IncidentAssert extends WithAssertionConfiguration<IncidentAssert> {

  /**
   * Verifies that the incident is active. Incidents in state {@code ACTIVE}, {@code PENDING}, or
   * {@code MIGRATED} are considered active.
   *
   * <p>The assertion waits until the incident is active.
   *
   * @return the assertion object
   */
  IncidentAssert isActive();

  /**
   * Verifies that the incident is resolved.
   *
   * <p>The assertion waits until the incident is resolved.
   *
   * @return the assertion object
   */
  IncidentAssert isResolved();

  /**
   * Verifies that the incident has the expected error type.
   *
   * <p>The assertion waits until the incident has the expected error type.
   *
   * @param errorType the expected error type
   * @return the assertion object
   */
  IncidentAssert hasErrorType(IncidentErrorType errorType);

  /**
   * Verifies that the incident has the expected error message.
   *
   * <p>The assertion waits until the incident has the expected error message.
   *
   * @param errorMessage the expected error message
   * @return the assertion object
   */
  IncidentAssert hasErrorMessage(String errorMessage);

  /**
   * Verifies that the incident belongs to the expected BPMN element.
   *
   * <p>The assertion waits until the incident has the expected element ID.
   *
   * @param elementId the expected BPMN element ID
   * @return the assertion object
   */
  IncidentAssert hasElementId(String elementId);

  /**
   * Verifies that the incident belongs to the expected process instance.
   *
   * <p>The assertion waits until the incident has the expected process instance key.
   *
   * @param processInstanceKey the expected process instance key
   * @return the assertion object
   */
  IncidentAssert hasProcessInstanceKey(long processInstanceKey);
}
