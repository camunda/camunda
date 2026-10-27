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
package io.camunda.client.api.search.response;

import io.camunda.client.api.search.enums.AgentDefinitionType;

/** Represents an agent definition returned from the Camunda REST API. */
public interface AgentDefinition {

  /** Returns the unique key identifying this agent definition. */
  long getAgentDefinitionKey();

  /** Returns the kind of agent this agent definition describes. */
  AgentDefinitionType getAgentType();

  /**
   * Returns the human-readable name of the process element that owns the agent definition. Falls
   * back to the element ID when the element has no BPMN name configured.
   */
  String getName();

  /** Returns the BPMN element ID of the process element that owns the agent definition. */
  String getElementId();

  /** Returns the BPMN process ID of the process definition that owns the agent definition. */
  String getProcessDefinitionId();

  /** Returns the key of the process definition that owns the agent definition. */
  long getProcessDefinitionKey();

  /** Returns the version of the process definition that owns the agent definition. */
  int getProcessDefinitionVersion();

  /**
   * Returns the version tag of the process definition that owns the agent definition, or {@code
   * null} if none was configured.
   */
  String getProcessDefinitionVersionTag();

  /** Returns the tenant ID of this agent definition. */
  String getTenantId();
}
