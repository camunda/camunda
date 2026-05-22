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
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableAgentInstanceRecordValue.Builder.class)
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
   * @return the full list of element instance keys this agent instance has been associated with.
   *     The most recently associated key is also available as {@link #getElementInstanceKey()}.
   */
  List<Long> getElementInstanceKeys();

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
   * @return the BPMN process ID of the process definition containing this agent
   */
  String getBpmnProcessId();

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
  AgentInstanceDefinitionValue getDefinition();

  /**
   * @return the limits configured for the agent instance
   */
  AgentInstanceLimitsValue getLimits();

  /**
   * @return the metrics collected during the agent instance execution
   */
  AgentInstanceMetricsValue getMetrics();

  /**
   * @return the list of tools available to the agent
   */
  List<AgentInstanceToolValue> getTools();

  /**
   * @return the names of attributes this command intends to update (on UPDATE) or the names of the
   *     attributes that were actually updated (on UPDATED); empty on CREATED and on the initial
   *     command form for CREATE
   */
  List<String> getChangedAttributes();

  /** Represents a tool available to an agent. */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableAgentInstanceToolValue.Builder.class)
  interface AgentInstanceToolValue {
    /**
     * @return the name of the tool
     */
    String getName();

    /**
     * @return the description of what the tool does
     */
    String getDescription();

    /**
     * @return the ID of the element that provides this tool
     */
    String getElementId();
  }

  /** Represents the definition details of an agent. */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableAgentInstanceDefinitionValue.Builder.class)
  interface AgentInstanceDefinitionValue {
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

  /** Represents the operational limits configured for an agent instance. */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableAgentInstanceLimitsValue.Builder.class)
  interface AgentInstanceLimitsValue {
    /**
     * @return the maximum number of tokens allowed for the agent instance
     */
    long getMaxTokens();

    /**
     * @return the maximum number of model calls allowed
     */
    int getMaxModelCalls();

    /**
     * @return the maximum number of tool calls allowed
     */
    int getMaxToolCalls();
  }

  /**
   * Represents metrics collected during agent instance execution.
   *
   * <p>On UPDATE commands, each field is interpreted as a delta to add to the stored value: {@code
   * -1} signals "field not provided" (the stored value is left untouched), and any non-negative
   * value is added to the stored counter. Negative deltas other than {@code -1} are rejected. On
   * CREATED / UPDATED events the fields carry the current absolute counter values.
   */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableAgentInstanceMetricsValue.Builder.class)
  interface AgentInstanceMetricsValue {
    /**
     * @return on events, the current number of input tokens used; on UPDATE, the delta to add
     *     ({@code -1} means the field is not provided)
     */
    long getInputTokens();

    /**
     * @return on events, the current number of output tokens generated; on UPDATE, the delta to add
     *     ({@code -1} means the field is not provided)
     */
    long getOutputTokens();

    /**
     * @return on events, the current number of model calls made by the agent; on UPDATE, the delta
     *     to add ({@code -1} means the field is not provided)
     */
    int getModelCalls();

    /**
     * @return on events, the current number of tool calls made by the agent; on UPDATE, the delta
     *     to add ({@code -1} means the field is not provided)
     */
    int getToolCalls();
  }
}
