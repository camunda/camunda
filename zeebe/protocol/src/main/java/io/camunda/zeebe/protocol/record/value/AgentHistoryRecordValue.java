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
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableAgentHistoryRecordValue.Builder.class)
public interface AgentHistoryRecordValue extends RecordValue, TenantOwned, ProcessInstanceRelated {

  /** Returns the system-generated key for this history entry. */
  long getAgentHistoryKey();

  /** Returns the key of the agent instance that produced this history entry. */
  long getAgentInstanceKey();

  /** Returns the key of the element instance associated with this entry. */
  long getElementInstanceKey();

  /**
   * @return the key of the process instance containing this agent history entry
   */
  @Override
  long getProcessInstanceKey();

  /** Returns the key of the root process instance in the hierarchy. */
  long getRootProcessInstanceKey();

  /** Returns the BPMN process ID of the process definition associated with this entry. */
  String getBpmnProcessId();

  /**
   * @return the key of the process definition
   */
  @Override
  long getProcessDefinitionKey();

  /**
   * @return the ID of the tenant that owns this agent history entry
   */
  @Override
  String getTenantId();

  /** Returns the key of the job that triggered the agent for this entry. */
  long getJobKey();

  /**
   * Returns the job lease token identifying which job activation produced this history entry.
   *
   * <p>An empty string means the entry is not scoped to a specific activation and applies to
   * <b>all</b> pending items of the job, whereas a non-empty lease scopes it to the single
   * activation holding that lease.
   */
  String getJobLease();

  /**
   * Returns the loopIteration counter. A loop iteration is one pass through an AI agent's loop,
   * during which the model reasons, selects tools, evaluates the result, and decides whether to
   * continue. One iteration covers the input for the LLM call, the call itself, and the tools it
   * dispatches; the results of those tool calls are input to the next iteration.
   */
  int getLoopIteration();

  /** Returns the role of the message author (e.g. USER, ASSISTANT, TOOL_RESULT). */
  AgentHistoryRole getRole();

  /** Returns the epoch-millis timestamp at which this entry was produced. */
  long getProducedAt();

  /** Returns the list of content blocks in this history entry. */
  List<AgentHistoryMessageContentValue> getContent();

  /**
   * Returns the system prompt, as content blocks, as of this entry. Populated by CONFIGURATION
   * items; empty for other roles.
   */
  List<AgentHistoryMessageContentValue> getSystemPrompt();

  /** Returns the list of tool calls made during this history entry. */
  List<AgentHistoryEmbeddedToolCallValue> getToolCalls();

  /** Returns the metrics captured for this history entry. */
  AgentHistoryMetricsValue getMetrics();

  /** Returns the client-supplied identifier this item was created with. */
  String getHistoryItemId();

  /**
   * Returns the complete list of tools available to the agent as of this entry. Populated by
   * CONFIGURATION items; empty for other roles.
   */
  List<AgentInstanceRecordValue.AgentInstanceToolValue> getTools();

  /**
   * Returns the LLM model identifier as of this entry. Populated by CONFIGURATION items; empty for
   * other roles.
   */
  String getModel();

  /**
   * Returns the LLM provider as of this entry. Populated by CONFIGURATION items; empty for other
   * roles.
   */
  String getProvider();

  /**
   * Returns the operational limits as of this entry. Populated by CONFIGURATION items; unused for
   * other roles.
   */
  AgentInstanceRecordValue.AgentInstanceLimitsValue getLimits();

  /**
   * Returns the names of attributes this entry intends to update, or the names of the attributes
   * that were actually updated; empty otherwise. Reserved for CONFIGURATION items once the engine
   * processing that populates it lands (see #58791); unused until then.
   */
  List<String> getChangedAttributes();

  /**
   * Returns whether this entry was recognized as a duplicate of a previously-processed history item
   * with the same historyItemId (scoped to the producing agent instance's whole lifetime) or of a
   * still-pending item under the current job lease — meaningful only when this entry is echoed back
   * embedded in an AgentInstanceRecord's history[]; if true, no new AGENT_HISTORY event was
   * actually created for it and agentHistoryKey is the ORIGINAL entry's key, not a new one.
   */
  boolean isDuplicate();

  /** Represents a single content block in a history entry message. */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableAgentHistoryMessageContentValue.Builder.class)
  interface AgentHistoryMessageContentValue {
    /** Returns the type of this content block (e.g. TEXT, DOCUMENT, OBJECT). */
    AgentHistoryContentType getContentType();

    /** Returns the text payload; non-empty when contentType is TEXT. */
    String getText();

    /** Returns the document reference; populated when contentType is DOCUMENT. */
    DocumentReferenceValue getDocumentReference();

    /**
     * Returns the JSON value payload when contentType is OBJECT; {@code null} otherwise. The value
     * may be any JSON type: a {@link java.util.Map} for objects, {@link java.util.List} for arrays,
     * or a scalar ({@link Integer}, {@link Long}, {@link Float}, {@link Double}, {@link Boolean},
     * {@link String}).
     */
    Object getObject();
  }

  /** Represents a tool call embedded in this history entry. */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableAgentHistoryEmbeddedToolCallValue.Builder.class)
  interface AgentHistoryEmbeddedToolCallValue {
    /** Returns the unique identifier of this tool call. */
    String getToolCallId();

    /** Returns the name of the tool that was called. */
    String getToolName();

    /** Returns the element id of the tool task element. */
    String getElementId();

    /** Returns the arguments passed to the tool call. */
    Map<String, Object> getArguments();
  }

  /** Represents metrics captured for this history entry. */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableAgentHistoryMetricsValue.Builder.class)
  interface AgentHistoryMetricsValue {
    /** Returns the number of input tokens consumed. */
    long getInputTokens();

    /** Returns the number of output tokens produced. */
    long getOutputTokens();

    /** Returns the number of reasoning tokens consumed by this LLM call. */
    long getReasoningTokenCount();

    /** Returns the number of cache-creation tokens consumed by this LLM call. */
    long getCacheCreationTokenCount();

    /** Returns the number of cache-read tokens consumed by this LLM call. */
    long getCacheReadTokenCount();

    /** Returns the wall-clock duration of the LLM call in milliseconds. */
    long getDurationMs();
  }
}
