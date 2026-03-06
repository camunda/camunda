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

/** A step in the conditional scenario builder that expects an action to be defined. */
public interface ConditionalScenarioConditionStep {

  /**
   * Defines the action to execute when the condition is satisfied.
   *
   * @param action the action to execute
   * @return the next step for chaining additional actions or defining new scenarios
   */
  ConditionalScenarioActionStep then(Runnable action);
}
