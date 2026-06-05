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

  /** Returns the job lease token identifying which job activation produced this history entry. */
  String getJobLease();

  /** Returns the iteration counter (conversation round with the LLM). */
  int getIteration();

  /** Returns the role of the message author (e.g. USER, ASSISTANT, TOOL_RESULT). */
  AgentHistoryRole getRole();

  /** Returns the commit status of this history entry (e.g. PENDING, COMMITTED, DISCARDED). */
  AgentHistoryCommitStatus getCommitStatus();

  /** Returns the epoch-millis timestamp at which this entry was produced. */
  long getProducedAt();

  /** Returns the list of content blocks in this history entry. */
  List<AgentHistoryMessageContentValue> getContent();

  /** Returns the list of tool calls made during this history entry. */
  List<AgentHistoryEmbeddedToolCallValue> getToolCalls();

  /** Returns the reference to the tool call associated with this history entry. */
  AgentHistoryToolCallRefValue getToolCallRef();

  /** Returns the metrics captured for this history entry. */
  AgentHistoryMetricsValue getMetrics();

  /** Represents a document reference associated with a content block. */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableAgentHistoryDocumentReferenceValue.Builder.class)
  interface AgentHistoryDocumentReferenceValue {
    /** Returns the unique identifier of the document. */
    String getDocumentId();

    /** Returns the identifier of the store where the document is held. */
    String getStoreId();
  }

  /** Represents a single content block in a history entry message. */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableAgentHistoryMessageContentValue.Builder.class)
  interface AgentHistoryMessageContentValue {
    /** Returns the type of this content block (e.g. TEXT, DOCUMENT, OBJECT). */
    AgentHistoryContentType getContentType();

    /** Returns the text payload; non-empty when contentType is TEXT. */
    String getText();

    /** Returns the document reference; populated when contentType is DOCUMENT. */
    AgentHistoryDocumentReferenceValue getDocumentReference();

    /** Returns the structured object payload; populated when contentType is OBJECT. */
    Map<String, Object> getObject();
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

  /** Represents a reference to a tool call associated with this history entry. */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableAgentHistoryToolCallRefValue.Builder.class)
  interface AgentHistoryToolCallRefValue {
    /** Returns the unique identifier of the referenced tool call. */
    String getToolCallId();

    /** Returns the name of the tool that was called. */
    String getToolName();

    /** Returns the element id of the tool task element. */
    String getElementId();

    /** Returns the key of the element instance for the tool task. */
    long getToolElementInstanceKey();
  }

  /** Represents metrics captured for this history entry. */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableAgentHistoryMetricsValue.Builder.class)
  interface AgentHistoryMetricsValue {
    /** Returns the number of input tokens consumed. */
    long getInputTokens();

    /** Returns the number of output tokens produced. */
    long getOutputTokens();

    /** Returns the wall-clock duration of the LLM call in milliseconds. */
    long getDurationMs();
  }
}
