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

/**
 * A step in the conditional scenario builder that allows chaining additional actions or defining
 * new scenarios.
 */
public interface ConditionalScenarioActionStep {

  /**
   * Chains an additional action for repeated condition matches. Actions are consumed in order:
   * first match fires the first action, second match fires the second, and the last action repeats
   * indefinitely.
   *
   * @param action the action to execute on the next condition match
   * @return this step for further chaining
   */
  ConditionalScenarioActionStep then(Runnable action);

  /**
   * Defines a new conditional scenario with the given condition.
   *
   * @param condition the condition to evaluate
   * @return the condition step for defining the action
   */
  ConditionalScenarioConditionStep when(ScenarioCondition condition);
}
