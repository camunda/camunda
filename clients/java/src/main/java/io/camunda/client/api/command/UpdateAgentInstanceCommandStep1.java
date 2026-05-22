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
   * Sets the element instance key associated with this agent instance. Used to validate that the
   * update targets the correct process instance.
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
}
