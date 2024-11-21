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

/** Enumerates listener event types associated with jobs */
public enum JobListenerEventType {

  /** Default */
  UNSPECIFIED,

  /**
   * Represents the `start` event for an execution listener. This event type is used to indicate
   * that the listener should be triggered at the start of an execution, such as the beginning of a
   * process instance, sub-process or element (task, event, gateway).
   */
  START,

  /**
   * Represents the `end` event for an execution listener. This event type is used to indicate that
   * the listener should be triggered at the end of an execution, such as the completion of a
   * process instance, sub-process or element (task, event, gateway).
   */
  END,

  /**
   * Represents the `complete` event for a task listener. This event type is used to indicate that
   * the listener should be triggered after a user task complete operation was invoked.
   */
  COMPLETE,

  /**
   * Represents the `assignment` event for a task listener. This event type is used to indicate that
   * the listener should be triggered when a user task is assigned, claimed (assigned to the current
   * user), or unassigned.
   */
  ASSIGNMENT
}
