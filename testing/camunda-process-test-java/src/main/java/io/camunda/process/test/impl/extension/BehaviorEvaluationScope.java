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
package io.camunda.process.test.impl.extension;

/**
 * Wraps the entire behavior evaluation cycle (condition checks and action executions) with the
 * appropriate context, such as an instant-probe await behavior override. The engine delegates to
 * this scope each polling cycle so that both conditions and actions run within the same context.
 */
@FunctionalInterface
public interface BehaviorEvaluationScope {

  /**
   * Executes the given evaluation within the appropriate context.
   *
   * @param evaluation the behavior evaluation to run (condition checks and action firings)
   */
  void execute(Runnable evaluation);
}
