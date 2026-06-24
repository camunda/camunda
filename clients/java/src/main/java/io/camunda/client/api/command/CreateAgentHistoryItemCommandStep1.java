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
package io.camunda.client.api.command;

import io.camunda.client.api.response.CreateAgentHistoryItemResponse;
import io.camunda.client.api.response.DocumentReferenceResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents a request to append a conversation history item to an agent instance.
 *
 * <p>Usage example:
 *
 * <pre>
 *   CreateAgentHistoryItemResponse response = camundaClient
 *       .newCreateAgentHistoryItemCommand(agentInstanceKey)
 *       .elementInstanceKey(2251799813685248L)
 *       .jobKey(2251799813685249L)
 *       .role(AgentHistoryRole.USER)
 *       .content(List.of(AgentHistoryContent.text("Hello!")))
 *       .producedAt(OffsetDateTime.now())
 *       .send()
 *       .join();
 * </pre>
 */
public interface CreateAgentHistoryItemCommandStep1 {

  /**
   * Sets the element instance key of the element instance this history item belongs to.
   *
   * @param elementInstanceKey the key of the element instance. Must be greater than 0.
   * @return this builder for method chaining
   */
  CreateAgentHistoryItemCommandStep2 elementInstanceKey(long elementInstanceKey);

  interface CreateAgentHistoryItemCommandStep2 {

    /**
     * Sets the job key of the currently active job during which this item was produced.
     *
     * @param jobKey the key of the active job. Must be greater than 0.
     * @return this builder for method chaining
     */
    CreateAgentHistoryItemCommandStep3 jobKey(long jobKey);
  }

  interface CreateAgentHistoryItemCommandStep3 {

    /**
     * Sets the role of this history item in the conversation.
     *
     * @param role the conversation role. Must not be null.
     * @return this builder for method chaining
     */
    CreateAgentHistoryItemCommandStep4 role(AgentHistoryRole role);
  }

  interface CreateAgentHistoryItemCommandStep4 {

    /**
     * Sets the content blocks of this history item.
     *
     * @param content the list of content blocks. Must not be null; may be empty.
     * @return this builder for method chaining
     */
    CreateAgentHistoryItemCommandStep5 content(List<AgentHistoryContent> content);
  }

  interface CreateAgentHistoryItemCommandStep5 {

    /**
     * Sets the connector-side timestamp when this message was produced.
     *
     * @param producedAt the production timestamp. Must not be null.
     * @return this builder for method chaining
     */
    CreateAgentHistoryItemFinalCommandStep producedAt(OffsetDateTime producedAt);
  }

  interface CreateAgentHistoryItemFinalCommandStep
      extends FinalCommandStep<CreateAgentHistoryItemResponse> {

    /**
     * Sets the opaque job lease token received from the job activation response.
     *
     * <p>Job leasing is not yet enforced (#55033); this field is optional until support is added.
     *
     * @param jobLease the lease token. Must not be null or empty.
     * @return this builder for method chaining
     */
    CreateAgentHistoryItemFinalCommandStep jobLease(String jobLease);

    /**
     * Sets the sequential iteration number this item belongs to.
     *
     * @param iteration the iteration number.
     * @return this builder for method chaining
     */
    CreateAgentHistoryItemFinalCommandStep iteration(int iteration);

    /**
     * Sets the tool calls associated with this history item.
     *
     * @param toolCalls the list of tool calls. May be null.
     * @return this builder for method chaining
     */
    CreateAgentHistoryItemFinalCommandStep toolCalls(List<AgentHistoryToolCall> toolCalls);

    /**
     * Sets per-call token and latency metrics. Present on ASSISTANT items only.
     *
     * @param metrics the metrics. May be null.
     * @return this builder for method chaining
     */
    CreateAgentHistoryItemFinalCommandStep metrics(AgentHistoryMetrics metrics);
  }

  /** Role of a history item in the agent conversation. */
  enum AgentHistoryRole {
    USER,
    ASSISTANT,
    TOOL_RESULT
  }

  /**
   * Factory for content blocks passed to {@link CreateAgentHistoryItemCommandStep4#content}. Use
   * these instead of the generated protocol classes to avoid explicit casts.
   *
   * <p>Example:
   *
   * <pre>
   *   .content(List.of(
   *       AgentHistoryContent.text("I can help."),
   *       AgentHistoryContent.object(Map.of("key", "value")),
   *       AgentHistoryContent.document(documentReferenceResponse)
   *   ))
   * </pre>
   */
  interface AgentHistoryContent {

    /**
     * Creates a plain-text content block.
     *
     * @param text the text. Must not be null.
     * @return an {@link AgentHistoryContent} of type TEXT
     */
    static AgentHistoryContent text(final String text) {
      if (text == null) {
        throw new IllegalArgumentException("text must not be null");
      }
      return new TextContent(text);
    }

    /**
     * Creates an arbitrary-object content block.
     *
     * @param object the key-value map. Must not be null.
     * @return an {@link AgentHistoryContent} of type OBJECT
     */
    static AgentHistoryContent object(final Map<String, Object> object) {
      if (object == null) {
        throw new IllegalArgumentException("object must not be null");
      }
      return new ObjectContent(object);
    }

    /**
     * Creates a document-reference content block from a {@link DocumentReferenceResponse} returned
     * by other document APIs (e.g. upload or create-link).
     *
     * @param documentReference the document reference. Must not be null.
     * @return a {@link DocumentContent} of type DOCUMENT
     */
    static DocumentContent document(final DocumentReferenceResponse documentReference) {
      if (documentReference == null) {
        throw new IllegalArgumentException("documentReference must not be null");
      }
      return new DocumentContent(documentReference);
    }

    /** Plain-text content block. */
    final class TextContent implements AgentHistoryContent {
      private final String text;

      TextContent(final String text) {
        this.text = text;
      }

      public String getText() {
        return text;
      }
    }

    /** Arbitrary-object content block. */
    final class ObjectContent implements AgentHistoryContent {
      private final Map<String, Object> object;

      ObjectContent(final Map<String, Object> object) {
        this.object = object;
      }

      public Map<String, Object> getObject() {
        return object;
      }
    }

    /** Document-reference content block. Use {@link AgentHistoryContent#document} to create. */
    final class DocumentContent implements AgentHistoryContent {
      private final DocumentReferenceResponse documentReference;

      DocumentContent(final DocumentReferenceResponse documentReference) {
        this.documentReference = documentReference;
      }

      public DocumentReferenceResponse getDocumentReference() {
        return documentReference;
      }
    }
  }

  /** Per-call token and latency metrics for an ASSISTANT history item. */
  final class AgentHistoryMetrics {
    private Long inputTokens;
    private Long outputTokens;
    private Long durationMs;

    public AgentHistoryMetrics inputTokens(final long inputTokens) {
      this.inputTokens = inputTokens;
      return this;
    }

    public AgentHistoryMetrics outputTokens(final long outputTokens) {
      this.outputTokens = outputTokens;
      return this;
    }

    public AgentHistoryMetrics durationMs(final long durationMs) {
      this.durationMs = durationMs;
      return this;
    }

    public Long getInputTokens() {
      return inputTokens;
    }

    public Long getOutputTokens() {
      return outputTokens;
    }

    public Long getDurationMs() {
      return durationMs;
    }
  }

  /** A tool call associated with an ASSISTANT history item. */
  final class AgentHistoryToolCall {
    private String toolCallId;
    private String toolName;
    private String elementId;
    private Map<String, Object> arguments;

    public AgentHistoryToolCall toolCallId(final String toolCallId) {
      this.toolCallId = toolCallId;
      return this;
    }

    public AgentHistoryToolCall toolName(final String toolName) {
      this.toolName = toolName;
      return this;
    }

    public AgentHistoryToolCall elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    public AgentHistoryToolCall arguments(final Map<String, Object> arguments) {
      this.arguments = arguments;
      return this;
    }

    public String getToolCallId() {
      return toolCallId;
    }

    public String getToolName() {
      return toolName;
    }

    public String getElementId() {
      return elementId;
    }

    public Map<String, Object> getArguments() {
      return arguments;
    }
  }
}
