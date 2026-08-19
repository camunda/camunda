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

import io.camunda.client.api.response.UpdateAgentInstanceResponse;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Represents a request to update an existing agent instance.
 *
 * <p>Usage example:
 *
 * <pre>
 *   camundaClient
 *       .newUpdateAgentInstanceCommand(agentInstanceKey)
 *       .elementInstanceKey(elementInstanceKey)
 *       .status(AgentInstanceUpdateStatus.THINKING)
 *       .send()
 *       .join();
 * </pre>
 */
public interface UpdateAgentInstanceCommandStep1 {

  /**
   * Sets the currently-active element instance for this agent instance. The engine uses this both
   * for ownership/equality validation and — when the supplied key differs from the previous
   * association (re-entry of an ad-hoc sub-process or AI Agent task) — to append the key to the
   * accumulated list of associated element instances.
   *
   * @param elementInstanceKey the key of the element instance. Must be greater than 0.
   * @return the next step of the builder
   */
  UpdateAgentInstanceCommandStep2 elementInstanceKey(long elementInstanceKey);

  interface UpdateAgentInstanceCommandStep2 extends FinalCommandStep<UpdateAgentInstanceResponse> {

    /**
     * Sets the new status of the agent instance.
     *
     * @param status the new status; see {@link AgentInstanceUpdateStatus} for available values
     * @return this builder for method chaining
     */
    UpdateAgentInstanceCommandStep2 status(AgentInstanceUpdateStatus status);

    /**
     * Increments the input token counter by the given delta.
     *
     * @param inputTokens the number of input tokens to add. Must be >= 0.
     * @return this builder for method chaining
     */
    UpdateAgentInstanceCommandStep2 inputTokens(long inputTokens);

    /**
     * Increments the output token counter by the given delta.
     *
     * @param outputTokens the number of output tokens to add. Must be >= 0.
     * @return this builder for method chaining
     */
    UpdateAgentInstanceCommandStep2 outputTokens(long outputTokens);

    /**
     * Increments the model call counter by the given delta.
     *
     * @param modelCalls the number of model calls to add. Must be >= 0.
     * @return this builder for method chaining
     */
    UpdateAgentInstanceCommandStep2 modelCalls(int modelCalls);

    /**
     * Increments the tool call counter by the given delta.
     *
     * @param toolCalls the number of tool calls to add. Must be >= 0.
     * @return this builder for method chaining
     */
    UpdateAgentInstanceCommandStep2 toolCalls(int toolCalls);

    /**
     * Replaces the full list of tools available to the agent instance. An empty list clears all
     * tools. Use {@link AgentTool#of(String)} or {@link AgentTool#of(String, String, String)} to
     * construct tool entries.
     *
     * <p>Example:
     *
     * <pre>
     *   .tools(List.of(
     *       AgentTool.of("search", "Search the web", "searchTask"),
     *       AgentTool.of("summarize")
     *   ))
     * </pre>
     *
     * @param tools the tools to set; pass an empty list to clear all tools
     * @return this builder for method chaining
     */
    UpdateAgentInstanceCommandStep2 tools(List<AgentTool> tools);

    /**
     * Sets the job key of the currently active job during which this update was produced. Required
     * whenever {@link #history(List)} is non-empty; otherwise irrelevant to the update.
     *
     * @param jobKey the key of the active job. Must be greater than 0.
     * @return this builder for method chaining
     */
    UpdateAgentInstanceCommandStep2 jobKey(long jobKey);

    /**
     * Sets the opaque job lease token received from the job activation response. Disambiguates this
     * activation from any other activation of the same job: if the job is later retried, history
     * items submitted under a superseded lease are discarded rather than committed.
     *
     * @param jobLease the lease token. Must not be null or blank.
     * @return this builder for method chaining
     */
    UpdateAgentInstanceCommandStep2 jobLease(String jobLease);

    /**
     * Replaces the full batch of conversation history items to append to the agent instance.
     * Requires {@link #jobKey(long)} to also be set.
     *
     * @param history the history items to append, in order. Must not be null; elements must not be
     *     null.
     * @return this builder for method chaining
     */
    UpdateAgentInstanceCommandStep2 history(List<HistoryItem> history);
  }

  /** Represents a tool available to the agent instance. */
  interface AgentTool {
    String getName();

    String getDescription();

    String getElementId();

    /**
     * Creates a tool with the given name and no description or element ID.
     *
     * @param name the tool name. Must not be blank.
     * @return a new {@link AgentTool}
     */
    static AgentTool of(final String name) {
      return of(name, null, null);
    }

    /**
     * Creates a tool with the given name, description, and element ID.
     *
     * @param name the tool name. Must not be blank.
     * @param description optional description of the tool
     * @param elementId optional ID of the BPMN element providing this tool
     * @return a new {@link AgentTool}
     */
    static AgentTool of(final String name, final String description, final String elementId) {
      return new AgentTool() {
        @Override
        public String getName() {
          return name;
        }

        @Override
        public String getDescription() {
          return description;
        }

        @Override
        public String getElementId() {
          return elementId;
        }
      };
    }
  }

  /**
   * A snapshot of the agent instance's operational limits, as recorded on a CONFIGURATION history
   * item.
   */
  interface AgentInstanceLimits {
    long getMaxTokens();

    int getMaxModelCalls();

    int getMaxToolCalls();

    /**
     * Creates a limits snapshot with the given values.
     *
     * @param maxTokens the token limit. Use -1 for no limit.
     * @param maxModelCalls the model-call limit. Use -1 for no limit.
     * @param maxToolCalls the tool-call limit. Use -1 for no limit.
     * @return a new {@link AgentInstanceLimits}
     */
    static AgentInstanceLimits of(
        final long maxTokens, final int maxModelCalls, final int maxToolCalls) {
      return new AgentInstanceLimits() {
        @Override
        public long getMaxTokens() {
          return maxTokens;
        }

        @Override
        public int getMaxModelCalls() {
          return maxModelCalls;
        }

        @Override
        public int getMaxToolCalls() {
          return maxToolCalls;
        }
      };
    }
  }

  /** A single conversation history item to append as part of an update batch. */
  final class HistoryItem {
    private String historyItemId;
    private int loopIteration;
    private AgentInstanceHistoryRole role;
    private List<AgentInstanceHistoryContent> content;
    private OffsetDateTime producedAt;
    private List<AgentInstanceHistoryToolCall> toolCalls;
    private AgentInstanceHistoryMetrics metrics;
    private List<AgentTool> tools;
    private String model;
    private String provider;
    private AgentInstanceLimits limits;
    private List<AgentInstanceHistoryContent> systemPrompt;

    /**
     * Sets the caller-assigned identifier used to detect and dedupe retries of the same item.
     *
     * @param historyItemId the item id. Must not be null or blank.
     * @return this builder for method chaining
     */
    public HistoryItem historyItemId(final String historyItemId) {
      this.historyItemId = historyItemId;
      return this;
    }

    /**
     * Sets the loop iteration this item belongs to: one pass through the agent's loop, during which
     * the model reasons, selects tools, evaluates the result, and decides whether to continue.
     * Grouping items by iteration makes a single pass addressable, so a slice of the conversation
     * history can be pointed at rather than the whole conversation.
     *
     * @param loopIteration the loop iteration number. Must be greater than 0, and increase with
     *     each iteration.
     * @return this builder for method chaining
     */
    public HistoryItem loopIteration(final int loopIteration) {
      this.loopIteration = loopIteration;
      return this;
    }

    /**
     * Sets the role of this history item in the conversation.
     *
     * @param role the conversation role. Must not be null.
     * @return this builder for method chaining
     */
    public HistoryItem role(final AgentInstanceHistoryRole role) {
      this.role = role;
      return this;
    }

    /**
     * Sets the content blocks of this history item.
     *
     * @param content the list of content blocks. Must not be null or empty.
     * @return this builder for method chaining
     */
    public HistoryItem content(final List<AgentInstanceHistoryContent> content) {
      this.content = content;
      return this;
    }

    /**
     * Sets the agent-side timestamp when this message was produced.
     *
     * @param producedAt the production timestamp. Must not be null.
     * @return this builder for method chaining
     */
    public HistoryItem producedAt(final OffsetDateTime producedAt) {
      this.producedAt = producedAt;
      return this;
    }

    /**
     * Sets the tool calls associated with this history item.
     *
     * @param toolCalls the list of tool calls. May be null.
     * @return this builder for method chaining
     */
    public HistoryItem toolCalls(final List<AgentInstanceHistoryToolCall> toolCalls) {
      this.toolCalls = toolCalls;
      return this;
    }

    /**
     * Sets per-call token and latency metrics. Present on ASSISTANT items only.
     *
     * @param metrics the metrics. May be null.
     * @return this builder for method chaining
     */
    public HistoryItem metrics(final AgentInstanceHistoryMetrics metrics) {
      this.metrics = metrics;
      return this;
    }

    /**
     * Sets the tools available to the agent as of this entry. CONFIGURATION items only.
     *
     * @param tools the tools. May be null.
     * @return this builder for method chaining
     */
    public HistoryItem tools(final List<AgentTool> tools) {
      this.tools = tools;
      return this;
    }

    /**
     * Sets the LLM model identifier as of this entry. CONFIGURATION items only.
     *
     * @param model the model identifier. May be null.
     * @return this builder for method chaining
     */
    public HistoryItem model(final String model) {
      this.model = model;
      return this;
    }

    /**
     * Sets the LLM provider as of this entry. CONFIGURATION items only.
     *
     * @param provider the provider identifier. May be null.
     * @return this builder for method chaining
     */
    public HistoryItem provider(final String provider) {
      this.provider = provider;
      return this;
    }

    /**
     * Sets the operational limits as of this entry. CONFIGURATION items only.
     *
     * @param limits the limits. May be null.
     * @return this builder for method chaining
     */
    public HistoryItem limits(final AgentInstanceLimits limits) {
      this.limits = limits;
      return this;
    }

    /**
     * Sets the system prompt, as content blocks, as of this entry. CONFIGURATION items only.
     *
     * @param systemPrompt the system prompt content blocks. May be null.
     * @return this builder for method chaining
     */
    public HistoryItem systemPrompt(final List<AgentInstanceHistoryContent> systemPrompt) {
      this.systemPrompt = systemPrompt;
      return this;
    }

    public String getHistoryItemId() {
      return historyItemId;
    }

    public int getLoopIteration() {
      return loopIteration;
    }

    public AgentInstanceHistoryRole getRole() {
      return role;
    }

    public List<AgentInstanceHistoryContent> getContent() {
      return content;
    }

    public OffsetDateTime getProducedAt() {
      return producedAt;
    }

    public List<AgentInstanceHistoryToolCall> getToolCalls() {
      return toolCalls;
    }

    public AgentInstanceHistoryMetrics getMetrics() {
      return metrics;
    }

    public List<AgentTool> getTools() {
      return tools;
    }

    public String getModel() {
      return model;
    }

    public String getProvider() {
      return provider;
    }

    public AgentInstanceLimits getLimits() {
      return limits;
    }

    public List<AgentInstanceHistoryContent> getSystemPrompt() {
      return systemPrompt;
    }
  }
}
