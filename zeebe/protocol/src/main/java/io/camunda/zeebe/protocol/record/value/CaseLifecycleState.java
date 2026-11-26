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

/** The state of a case. */
public enum CaseLifecycleState {
  /** The root process instance of the case is running. */
  ACTIVE,

  /** The case is paused; its root process instance does not make progress. */
  SUSPENDED,

  /** The root process instance of the case completed. */
  COMPLETED,

  /** The root process instance of the case was terminated before it finished. */
  TERMINATED
}
