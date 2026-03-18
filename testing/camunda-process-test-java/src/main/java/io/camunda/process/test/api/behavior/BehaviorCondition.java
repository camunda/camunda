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
package io.camunda.process.test.api.behavior;

/**
 * Functional interface representing a condition that is evaluated periodically by the conditional
 * behavior engine. A condition is considered met when the method completes without throwing. If the
 * condition is not satisfied, the method should throw an {@link AssertionError}, which is typically
 * produced by CPT assertions.
 */
@FunctionalInterface
public interface BehaviorCondition {

  /**
   * Verifies whether this condition is currently met. If the condition is not satisfied, an {@link
   * AssertionError} should be thrown — typically by using a CPT assertion such as {@code
   * assertThat(processInstance).hasActiveElement("taskA")}.
   *
   * @throws AssertionError if the condition is not satisfied
   */
  void verifyCondition() throws AssertionError;
}
