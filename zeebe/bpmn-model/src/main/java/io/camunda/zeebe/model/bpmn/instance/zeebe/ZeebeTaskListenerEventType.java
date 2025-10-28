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
package io.camunda.zeebe.model.bpmn.instance.zeebe;

/**
 * Represents the various event types for `task listeners` in a BPMN workflow. Task listeners allow
 * users to execute custom logic during specific lifecycle events of a user task in a process.
 *
 * <ul>
 *   <li>{@code create} - Triggered when the user task is created.
 *   <li>{@code assignment} - Triggered when the user task is assigned/claimed or unassigned.
 *   <li>{@code update} - Triggered when the user task details are updated.
 *   <li>{@code complete} - Triggered when the user task is completed.
 *   <li>{@code cancel} - Triggered when the user task is canceled.
 * </ul>
 */
public enum ZeebeTaskListenerEventType {
  /**
   * @deprecated use {@link #creating} instead
   */
  @Deprecated
  create,

  /**
   * @deprecated use {@link #assigning} instead
   */
  @Deprecated
  assignment,

  /**
   * @deprecated use {@link #updating} instead
   */
  @Deprecated
  update,

  /**
   * @deprecated use {@link #completing} instead
   */
  @Deprecated
  complete,

  /**
   * @deprecated use {@link #canceling} instead
   */
  @Deprecated
  cancel,

  creating,
  assigning,
  updating,
  completing,
  canceling;
}
