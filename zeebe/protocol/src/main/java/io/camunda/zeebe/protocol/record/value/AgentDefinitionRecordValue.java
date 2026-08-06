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
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableAgentDefinitionRecordValue.Builder.class)
public interface AgentDefinitionRecordValue extends RecordValue, TenantOwned {

  /**
   * @return the unique key of the agent definition
   */
  long getAgentDefinitionKey();

  /**
   * @return the kind of agent this definition describes
   */
  AgentDefinitionType getAgentType();

  /**
   * @return the human-readable name of the process element that owns the agent
   */
  String getName();

  /**
   * @return the ID of the process element that owns the agent
   */
  String getElementId();

  /**
   * @return the BPMN process ID of the process definition that owns the agent
   */
  String getBpmnProcessId();

  /**
   * @return the key of the process definition that owns the agent
   */
  long getProcessDefinitionKey();

  /**
   * @return the version of the process definition that owns the agent
   */
  int getProcessDefinitionVersion();

  /**
   * @return the version tag of the process definition that owns the agent
   */
  String getProcessDefinitionVersionTag();

  /**
   * @return the ID of the tenant that owns this agent definition
   */
  @Override
  String getTenantId();
}
