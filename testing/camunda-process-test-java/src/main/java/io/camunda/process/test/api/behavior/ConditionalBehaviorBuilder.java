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
 * Fluent builder for defining a conditional behavior — one condition with one or more chained
 * actions. Actions are consumed in order on each condition match, with the last action repeating
 * indefinitely once all preceding actions are exhausted.
 */
public interface ConditionalBehaviorBuilder {

  /**
   * Assigns a descriptive name to this conditional behavior, used in log messages and diagnostics.
   * If not called, the behavior is identified by its 1-based registration index (e.g. {@code #1}).
   *
   * <p>This always sets the name of the entire conditional behavior, regardless of where it appears
   * in the builder chain. Calling it after {@code .then()} overrides any previously set name rather
   * than naming the individual action.
   *
   * @param name a non-blank descriptive name
   * @return this builder for further chaining
   * @throws IllegalArgumentException if name is null or blank
   */
  ConditionalBehaviorBuilder as(String name);

  /**
   * Defines an action to execute when the condition is satisfied. Actions are consumed in order:
   * first match fires the first action, second match fires the second, and the last action repeats
   * indefinitely.
   *
   * @param action the action to execute
   * @return this builder for further chaining
   */
  ConditionalBehaviorBuilder then(Runnable action);
}
