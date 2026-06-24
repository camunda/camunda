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

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import java.util.List;
import org.immutables.value.Value;

/**
 * Represents a process incident.
 *
 * <p>See {@link IncidentIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableIncidentRecordValue.Builder.class)
public interface IncidentRecordValue
    extends RecordValue, ProcessInstanceRelated, AuditLogProcessInstanceRelated, TenantOwned {
  /**
   * @return the type of error this incident is caused by. Can be <code>UNKNOWN</code> if the
   *     incident record is part of a {@link IncidentIntent#RESOLVE} command.
   */
  ErrorType getErrorType();

  /**
   * @return the description of the error this incident is caused by. Can be empty if the incident
   *     record is part of a {@link IncidentIntent#RESOLVE} command.
   */
  String getErrorMessage();

  /**
   * @return the key of the process instance this incident belongs to. Can be <code>-1</code> if the
   *     incident record is part of a {@link IncidentIntent#RESOLVE} command.
   */
  @Override
  long getProcessInstanceKey();

  /**
   * @return the key of the process this incident belongs to. Can be <code>-1</code> if the incident
   *     record is part of a {@link IncidentIntent#RESOLVE} command.
   */
  @Override
  long getProcessDefinitionKey();

  /**
   * @return the id of the element this incident belongs to. Can be empty if incident record is part
   *     of a {@link IncidentIntent#RESOLVE} command.
   */
  String getElementId();

  /**
   * @return the key of the element instance this incident belongs to. Can be <code>-1</code> if the
   *     incident record is part of a {@link IncidentIntent#RESOLVE} command.
   */
  @Override
  long getElementInstanceKey();

  /**
   * @return the BPMN process id this incident belongs to. Can be empty if the incident record is
   *     part of a {@link IncidentIntent#RESOLVE} command.
   */
  @Override
  String getBpmnProcessId();

  /**
   * @return the key of the job this incident belongs to. Can be <code>-1</code> if the incident
   *     belongs to no job or the incident record is part of a {@link IncidentIntent#RESOLVE}
   *     command.
   */
  long getJobKey();

  /**
   * @return the key of the variable scope on which the variables need to be updated. Can be <code>
   *     -1</code> if the incident record is part of a {@link IncidentIntent#RESOLVE} command.
   */
  long getVariableScopeKey();

  /**
   * @return tree path information about all element instances in the call hierarchy leading to an
   *     incident. It contains an entry per process instance in the hierarchy of the incident, each
   *     contains all the instance keys of all the elements in the call hierarchy within this
   *     process instance
   */
  List<List<Long>> getElementInstancePath();

  /**
   * @return tree path information for all process definitions in the hierarchy of the incident.
   *     Each entry is a process definition key for the corresponding process instance
   */
  List<Long> getProcessDefinitionPath();

  /**
   * @return tree path information about call activities in the hierarchy of the incident. Each
   *     entry is a reference to the call activity in BPMN model containing an incident.
   */
  List<Integer> getCallingElementPath();

  /**
   * Returns the key of the root process instance in the hierarchy. For top-level process instances,
   * this is equal to {@link #getProcessInstanceKey()}. For child process instances (created via
   * call activities), this is the key of the topmost parent process instance.
   *
   * <p>Important: This value is only set for incidents created after version 8.8.0. For older
   * incidents, the method will return -1.
   *
   * @return the key of the root process instance, or {@code -1} if not set
   */
  long getRootProcessInstanceKey();
}
