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
package io.zeebe.exporter.api.record.value;

import io.zeebe.exporter.api.record.RecordValue;
import io.zeebe.protocol.intent.IncidentIntent;

/**
 * Represents a workflow incident.
 *
 * <p>See {@link IncidentIntent} for intents.
 */
public interface IncidentRecordValue extends RecordValue {
  /**
   * @return the type of error this incident is caused by. Can be <code>UNKNOWN</code> if the
   *     incident record is part of a {@link IncidentIntent#RESOLVE} command.
   */
  String getErrorType();

  /**
   * @return the description of the error this incident is caused by. Can be empty if the incident
   *     record is part of a {@link IncidentIntent#RESOLVE} command.
   */
  String getErrorMessage();

  /**
   * @return the BPMN process id this incident belongs to. Can be empty if the incident record is
   *     part of a {@link IncidentIntent#RESOLVE} command.
   */
  String getBpmnProcessId();

  /**
   * @return the key of the workflow this incident belongs to. Can be <code>-1</code> if the
   *     incident record is part of a {@link IncidentIntent#RESOLVE} command.
   */
  long getWorkflowKey();

  /**
   * @return the key of the workflow instance this incident belongs to. Can be <code>-1</code> if
   *     the incident record is part of a {@link IncidentIntent#RESOLVE} command.
   */
  long getWorkflowInstanceKey();

  /**
   * @return the id of the element this incident belongs to. Can be empty if incident record is part
   *     of a {@link IncidentIntent#RESOLVE} command.
   */
  String getElementId();

  /**
   * @return the key of the element instance this incident belongs to. Can be <code>-1</code> if the
   *     incident record is part of a {@link IncidentIntent#RESOLVE} command.
   */
  long getElementInstanceKey();

  /**
   * @return the key of the job this incident belongs to. Can be <code>-1</code> if the incident
   *     belongs to no job or the incident record is part of a {@link IncidentIntent#RESOLVE}
   *     command.
   */
  long getJobKey();

  /**
   * @return the key of the element instance to use in order to update the correct variables before.
   *     Can be <code>-1</code> if the incident record is part of a {@link IncidentIntent#RESOLVE}
   *     command.
   */
  long getVariableScopeKey();
}
