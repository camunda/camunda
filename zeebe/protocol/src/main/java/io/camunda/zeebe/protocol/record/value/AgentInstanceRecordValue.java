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

/**
 * Represents an agent instance in the Zeebe protocol.
 *
 * <p>An agent instance is the engine-side representation of an AI agent execution's shared context.
 * It tracks the agent's definition, aggregate metrics (token usage, model calls, tool calls), and
 * status.
 *
 * <p><b>Metric semantics differ by record type:</b> On UPDATE commands, the metric fields ({@link
 * #getInputTokens()}, {@link #getOutputTokens()}, {@link #getModelCalls()}, {@link
 * #getToolCalls()}) carry <em>deltas</em> (increments from a single interaction). On UPDATED
 * events, the same fields carry the <em>aggregated totals</em> after the engine has applied the
 * deltas to its state. This allows the connector to report consumption incrementally while the
 * engine maintains the running totals.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableAgentInstanceRecordValue.Builder.class)
public interface AgentInstanceRecordValue extends RecordValue, ProcessInstanceRelated, TenantOwned {

  /** Returns the key of the agent instance. */
  long getAgentInstanceKey();

  /** Returns the key of the element instance (Ad-Hoc Sub-Process) this agent belongs to. */
  long getElementInstanceKey();

  /** Returns the BPMN element ID of the Ad-Hoc Sub-Process this agent belongs to. */
  String getElementId();

  /** Returns the current status of the agent instance. */
  AgentInstanceStatus getStatus();

  /** Returns the name of the LLM model used by this agent. */
  String getModel();

  /** Returns the LLM provider for this agent. */
  String getProvider();

  /** Returns the system prompt configured for this agent. */
  String getSystemPrompt();

  /**
   * Returns input token count. On UPDATE commands this is a delta; on UPDATED events this is the
   * aggregate total.
   */
  long getInputTokens();

  /**
   * Returns output token count. On UPDATE commands this is a delta; on UPDATED events this is the
   * aggregate total.
   */
  long getOutputTokens();

  /**
   * Returns model call count. On UPDATE commands this is a delta; on UPDATED events this is the
   * aggregate total.
   */
  long getModelCalls();

  /**
   * Returns tool call count. On UPDATE commands this is a delta; on UPDATED events this is the
   * aggregate total.
   */
  long getToolCalls();

  /**
   * Returns the maximum number of tokens (input + output combined) this agent is allowed to
   * consume. Set once at creation, immutable afterwards. A value of {@code -1} means not set.
   */
  long getMaxTokens();

  /**
   * Returns the maximum number of model calls this agent is allowed to make. Set once at creation,
   * immutable afterwards. A value of {@code -1} means not set.
   */
  long getMaxModelCalls();

  /**
   * Returns the maximum number of tool calls this agent is allowed to make. Set once at creation,
   * immutable afterwards. A value of {@code -1} means not set.
   */
  long getMaxToolCalls();

  /** The status of an agent instance. */
  enum AgentInstanceStatus {
    IDLE,
    THINKING,
    CALLING_TOOL
  }
}
