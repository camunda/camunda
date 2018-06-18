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
package io.zeebe.client.api.record;

public interface IncidentRecord extends Record {
  /** @return the type of error this incident is caused by. */
  String getErrorType();

  /** @return the description of the error this incident is caused by. */
  String getErrorMessage();

  /**
   * @return the BPMN process id this incident belongs to. Can be <code>null</code> if the incident
   *     belongs to no workflow instance.
   */
  String getBpmnProcessId();

  /**
   * @return the key of the workflow instance this incident belongs to. Can be <code>null</code> if
   *     the incident belongs to no workflow instance.
   */
  Long getWorkflowInstanceKey();

  /**
   * @return the id of the activity this incident belongs to. Can be <code>null</code> if the
   *     incident belongs to no activity or workflow instance.
   */
  String getActivityId();

  /**
   * @return the key of the activity instance this incident belongs to. Can be <code>null</code> if
   *     the incident belongs to no activity or workflow instance.
   */
  Long getActivityInstanceKey();

  /**
   * @return the key of the job this incident belongs to. Can be <code>null</code> if the incident
   *     belongs to no task.
   */
  Long getJobKey();
}
