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
package io.camunda.zeebe.protocol.record.value;

/**
 * Defines the semantics of a variable update for a user task.
 *
 * <p>This enum is used to indicate how variable updates should be applied when interacting with a
 * user task, for example, via the SetVariables command.
 *
 * <ul>
 *   <li>{@code NULL} – Unspecified or not set.
 *   <li>{@code LOCAL} – Variables are set locally on the user task's element instance scope.
 *   <li>{@code PROPAGATE} – Variables are propagated to the closest parent scope where the variable
 *       is defined, or created at the process instance level if not yet defined.
 * </ul>
 */
public enum UserTaskVariablesUpdateSemantic {
  NULL,
  LOCAL,
  PROPAGATE,
}
