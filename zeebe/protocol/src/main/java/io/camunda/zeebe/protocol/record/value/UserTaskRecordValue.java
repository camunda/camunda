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
import io.camunda.zeebe.protocol.record.RecordValueWithVariables;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

/**
 * Represents a user task related event or command.
 *
 * <p>See {@link UserTaskIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableUserTaskRecordValue.Builder.class)
public interface UserTaskRecordValue
    extends RecordValueWithVariables, ProcessInstanceRelated, TenantOwned {

  long getUserTaskKey();

  String getAssignee();

  List<String> getCandidateGroupsList();

  List<String> getCandidateUsersList();

  String getDueDate();

  String getFollowUpDate();

  long getFormKey();

  List<String> getChangedAttributes();

  String getAction();

  String getExternalFormReference();

  Map<String, String> getCustomHeaders();

  long getCreationTimestamp();

  /**
   * @return the element id of the corresponding user task
   */
  String getElementId();

  /**
   * @return the element instance key of the corresponding user task
   */
  long getElementInstanceKey();

  /**
   * @return the bpmn process id of the corresponding process definition
   */
  String getBpmnProcessId();

  /**
   * @return the version of the corresponding process definition
   */
  int getProcessDefinitionVersion();

  /**
   * @return the process key of the corresponding process definition
   */
  long getProcessDefinitionKey();

  int getPriority();
}
