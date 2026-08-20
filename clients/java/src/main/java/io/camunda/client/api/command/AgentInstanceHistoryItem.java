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

import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A single conversation history item to append as part of a batch, submitted on either {@link
 * CreateAgentInstanceCommandStep1} or {@link UpdateAgentInstanceCommandStep1}.
 */
public final class AgentInstanceHistoryItem {
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
  public AgentInstanceHistoryItem historyItemId(final String historyItemId) {
    this.historyItemId = historyItemId;
    return this;
  }

  /**
   * Sets the loop iteration this item belongs to: one pass through the agent's loop, during which
   * the model reasons, selects tools, evaluates the result, and decides whether to continue.
   * Grouping items by iteration makes a single pass addressable, so a slice of the conversation
   * history can be pointed at rather than the whole conversation.
   *
   * @param loopIteration the loop iteration number. Must be greater than 0, and increase with each
   *     iteration.
   * @return this builder for method chaining
   */
  public AgentInstanceHistoryItem loopIteration(final int loopIteration) {
    this.loopIteration = loopIteration;
    return this;
  }

  /**
   * Sets the role of this history item in the conversation.
   *
   * @param role the conversation role. Must not be null.
   * @return this builder for method chaining
   */
  public AgentInstanceHistoryItem role(final AgentInstanceHistoryRole role) {
    this.role = role;
    return this;
  }

  /**
   * Sets the content blocks of this history item.
   *
   * @param content the list of content blocks. Must not be null or empty.
   * @return this builder for method chaining
   */
  public AgentInstanceHistoryItem content(final List<AgentInstanceHistoryContent> content) {
    this.content = content;
    return this;
  }

  /**
   * Sets the agent-side timestamp when this message was produced.
   *
   * @param producedAt the production timestamp. Must not be null.
   * @return this builder for method chaining
   */
  public AgentInstanceHistoryItem producedAt(final OffsetDateTime producedAt) {
    this.producedAt = producedAt;
    return this;
  }

  /**
   * Sets the tool calls associated with this history item.
   *
   * @param toolCalls the list of tool calls. May be null.
   * @return this builder for method chaining
   */
  public AgentInstanceHistoryItem toolCalls(final List<AgentInstanceHistoryToolCall> toolCalls) {
    this.toolCalls = toolCalls;
    return this;
  }

  /**
   * Sets per-call token and latency metrics. Present on ASSISTANT items only.
   *
   * @param metrics the metrics. May be null.
   * @return this builder for method chaining
   */
  public AgentInstanceHistoryItem metrics(final AgentInstanceHistoryMetrics metrics) {
    this.metrics = metrics;
    return this;
  }

  /**
   * Sets the tools available to the agent as of this entry. CONFIGURATION items only.
   *
   * @param tools the tools. May be null.
   * @return this builder for method chaining
   */
  public AgentInstanceHistoryItem tools(final List<AgentTool> tools) {
    this.tools = tools;
    return this;
  }

  /**
   * Sets the LLM model identifier as of this entry. CONFIGURATION items only.
   *
   * @param model the model identifier. May be null.
   * @return this builder for method chaining
   */
  public AgentInstanceHistoryItem model(final String model) {
    this.model = model;
    return this;
  }

  /**
   * Sets the LLM provider as of this entry. CONFIGURATION items only.
   *
   * @param provider the provider identifier. May be null.
   * @return this builder for method chaining
   */
  public AgentInstanceHistoryItem provider(final String provider) {
    this.provider = provider;
    return this;
  }

  /**
   * Sets the operational limits as of this entry. CONFIGURATION items only.
   *
   * @param limits the limits. May be null.
   * @return this builder for method chaining
   */
  public AgentInstanceHistoryItem limits(final AgentInstanceLimits limits) {
    this.limits = limits;
    return this;
  }

  /**
   * Sets the system prompt, as content blocks, as of this entry. CONFIGURATION items only.
   *
   * @param systemPrompt the system prompt content blocks. May be null.
   * @return this builder for method chaining
   */
  public AgentInstanceHistoryItem systemPrompt(
      final List<AgentInstanceHistoryContent> systemPrompt) {
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
