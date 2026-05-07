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

import io.camunda.zeebe.protocol.record.RecordValue;

public interface AgentInstanceRecordValue extends RecordValue, ProcessInstanceRelated, TenantOwned {

  /**
   * @return the unique key of the agent instance
   */
  long getAgentInstanceKey();

  /**
   * @return the unique key of the element instance that the agent instance was last associated with
   */
  long getElementInstanceKey();

  /**
   * @return the ID of the process element representing the agent
   */
  String getElementId();

  /**
   * @return the key of the process instance containing this agent
   */
  @Override
  long getProcessInstanceKey();

  /**
   * @return the key of the process definition
   */
  @Override
  long getProcessDefinitionKey();

  /**
   * @return the version of the process definition
   */
  int getProcessDefinitionVersion();

  /**
   * @return the version tag of the process definition
   */
  String getVersionTag();

  /**
   * @return the ID of the tenant that owns this agent instance
   */
  @Override
  String getTenantId();

  /**
   * @return the current status of the agent instance
   */
  AgentInstanceStatus getStatus();

  /**
   * @return the definition details of the agent
   */
  Definition getDefinition();

  interface Definition {
    /**
     * @return the model used by the agent
     */
    String getModel();

    /**
     * @return the provider of the agent model
     */
    String getProvider();

    /**
     * @return the system prompt used to configure the agent
     */
    String getSystemPrompt();
  }
}
