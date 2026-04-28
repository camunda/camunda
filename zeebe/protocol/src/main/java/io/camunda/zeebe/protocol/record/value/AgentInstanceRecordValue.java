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

  /**
   * The status of an agent instance.
   *
   * <p>An agent instance starts in {@link #INITIALIZING} after creation. From there it transitions
   * to either {@link #TOOL_DISCOVERY} (to discover the tools available to it) or directly to {@link
   * #THINKING}. {@link #THINKING} is the central state from which the agent either issues tool
   * calls ({@link #TOOL_CALLING}) or settles into {@link #IDLE} awaiting external input. Both
   * {@link #TOOL_CALLING} and {@link #IDLE} only transition back to {@link #THINKING}. From any
   * status the agent instance can be deleted, transitioning to {@link #DELETED}, which is terminal.
   * The engine may also reject any transition with {@link #LIMIT_EXCEEDED} when {@code maxTokens},
   * {@code maxModelCalls}, or {@code maxToolCalls} would be breached; this is also terminal.
   *
   * <p>State diagram:
   *
   * <pre>{@code
   *                  +----------------+
   *                  | INITIALIZING   |
   *                  +----------------+
   *                     |          |
   *                     v          v
   *              +--------------+  |
   *              |TOOL_DISCOVERY|  |
   *              +--------------+  |
   *                     |          |
   *                     v          v
   *                  +----------------+
   *             +--->|    THINKING    |<---+
   *             |    +----------------+    |
   *             |       |          |       |
   *             |       v          v       |
   *             |  +---------+  +------+   |
   *             +--|  IDLE   |  |TOOL_ |---+
   *                +---------+  |CALLING|
   *                             +------+
   *
   * (from any state) ----> DELETED         [terminal]
   * (from any state) ----> LIMIT_EXCEEDED  [terminal, engine-driven]
   * }</pre>
   *
   * <p>In the state-aligned intent design, status is redundant with the latest intent ({@code
   * DISCOVERING_TOOLS} → {@code TOOL_DISCOVERY}, {@code THINKING} → {@code THINKING}, etc.). It is
   * retained for entity-snapshot queries that don't want to derive state from intent history.
   */
  enum AgentInstanceStatus {
    /** Initial state right after creation, before the first update from the connector. */
    INITIALIZING,
    /** Agent is discovering the tools available to it. */
    TOOL_DISCOVERY,
    /** Agent is reasoning, typically performing a model call. */
    THINKING,
    /** Agent is executing a tool call. */
    TOOL_CALLING,
    /** Agent is waiting for external input (e.g. user response). */
    IDLE,
    /** Terminal state after the agent instance has been deleted. */
    DELETED,
    /** Terminal state after the engine rejected a transition for breaching a configured limit. */
    LIMIT_EXCEEDED
  }
}
