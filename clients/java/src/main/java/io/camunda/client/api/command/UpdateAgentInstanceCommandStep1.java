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
    UpdateAgentInstanceCommandStep2 history(List<AgentInstanceHistoryItem> history);
  }
}
