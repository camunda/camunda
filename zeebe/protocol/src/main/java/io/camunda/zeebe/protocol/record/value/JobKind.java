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

/** Enumerates the kinds of jobs managed by Zeebe */
public enum JobKind {
  /**
   * Represents jobs associated with BPMN elements. These are typically service tasks or any other
   * BPMN element that can be executed as part of a workflow instance. This is the default type for
   * backward compatibility and general-purpose tasks.
   */
  BPMN_ELEMENT,

  /**
   * Represents jobs specifically created for execution listeners. These jobs are triggered by the
   * workflow engine in response to execution events in the BPMN process, such as starting or
   * completing a task or an entire process.
   */
  EXECUTION_LISTENER,

  /**
   * Represents jobs created for task listeners. These jobs are associated with specific lifecycle
   * events of BPMN tasks, such as task creation, assignment, or completion. Task listeners allow
   * for custom logic to be executed in response to these events.
   */
  TASK_LISTENER,

  /**
   * Represents jobs created for ad-hoc sub-processes. These jobs are associated with the execution
   * of ad-hoc sub-processes within a BPMN workflow.
   */
  AD_HOC_SUB_PROCESS
}
