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

import io.camunda.client.api.response.CreateAgentInstanceResponse;
import java.util.List;

/**
 * Represents a request to create a new agent instance.
 *
 * <p>Usage example without a history batch, setting model/provider/systemPrompt directly:
 *
 * <pre>
 *   CreateAgentInstanceResponse response = camundaClient
 *       .newCreateAgentInstanceCommand()
 *       .elementInstanceKey(2251799813685248L)
 *       .model("gpt-4o")
 *       .provider("openai")
 *       .systemPrompt("You are a helpful assistant.")
 *       .send()
 *       .join();
 * </pre>
 *
 * <p>Usage example with a history batch, establishing model/provider/systemPrompt through a
 * CONFIGURATION history item instead:
 *
 * <pre>
 *   CreateAgentInstanceResponse response = camundaClient
 *       .newCreateAgentInstanceCommand()
 *       .elementInstanceKey(2251799813685248L)
 *       .jobKey(jobKey)
 *       .history(List.of(configurationItem, ...))
 *       .send()
 *       .join();
 * </pre>
 */
public interface CreateAgentInstanceCommandStep1 {

  /**
   * Sets the element instance key of the AHSP or AI Agent Task element instance.
   *
   * @param elementInstanceKey the key of the element instance. Must be greater than 0.
   * @return the next step of the builder
   */
  CreateAgentInstanceCommandStep2 elementInstanceKey(long elementInstanceKey);

  interface CreateAgentInstanceCommandStep2 extends FinalCommandStep<CreateAgentInstanceResponse> {

    /**
     * Sets the LLM model identifier for the agent instance. Must be omitted when {@link
     * #history(List)} is provided; establish it through a CONFIGURATION history item instead.
     * Required when no history is provided.
     *
     * @param model the model identifier (for example, gpt-4o). Must not be null or empty.
     * @return this builder for method chaining
     */
    CreateAgentInstanceCommandStep2 model(String model);

    /**
     * Sets the LLM provider for the agent instance. Must be omitted when {@link #history(List)} is
     * provided; establish it through a CONFIGURATION history item instead. Required when no history
     * is provided.
     *
     * @param provider the provider name (for example, openai). Must not be null or empty.
     * @return this builder for method chaining
     */
    CreateAgentInstanceCommandStep2 provider(String provider);

    /**
     * Sets the system prompt for the agent instance. Must be omitted when {@link #history(List)} is
     * provided; establish it through a CONFIGURATION history item instead. Required when no history
     * is provided.
     *
     * @param systemPrompt the system prompt text. Must not be null or empty.
     * @return this builder for method chaining
     */
    CreateAgentInstanceCommandStep2 systemPrompt(String systemPrompt);

    /**
     * Sets the maximum number of tokens the agent instance may use. Defaults to -1 (no limit). Must
     * be omitted when {@link #history(List)} is provided; establish it through a CONFIGURATION
     * history item instead, if needed.
     *
     * @param maxTokens the token limit. Use -1 for no limit.
     * @return this builder for method chaining
     */
    CreateAgentInstanceCommandStep2 maxTokens(long maxTokens);

    /**
     * Sets the maximum number of LLM model calls the agent instance may make. Defaults to -1 (no
     * limit). Must be omitted when {@link #history(List)} is provided; establish it through a
     * CONFIGURATION history item instead, if needed.
     *
     * @param maxModelCalls the model-call limit. Use -1 for no limit.
     * @return this builder for method chaining
     */
    CreateAgentInstanceCommandStep2 maxModelCalls(int maxModelCalls);

    /**
     * Sets the maximum number of tool calls the agent instance may make. Defaults to -1 (no limit).
     * Must be omitted when {@link #history(List)} is provided; establish it through a CONFIGURATION
     * history item instead, if needed.
     *
     * @param maxToolCalls the tool-call limit. Use -1 for no limit.
     * @return this builder for method chaining
     */
    CreateAgentInstanceCommandStep2 maxToolCalls(int maxToolCalls);

    /**
     * Sets the job key of the currently active job during which this creation was produced.
     * Required whenever {@link #history(List)} is non-empty; otherwise irrelevant to the creation.
     *
     * @param jobKey the key of the active job. Must be greater than 0.
     * @return this builder for method chaining
     */
    CreateAgentInstanceCommandStep2 jobKey(long jobKey);

    /**
     * Sets the opaque job lease token received from the job activation response. Disambiguates this
     * activation from any other activation of the same job: if the job is later retried, history
     * items submitted under a superseded lease are discarded rather than committed.
     *
     * @param jobLease the lease token. Must not be null or blank.
     * @return this builder for method chaining
     */
    CreateAgentInstanceCommandStep2 jobLease(String jobLease);

    /**
     * Sets the batch of conversation history items to append to the agent instance at creation.
     * Requires {@link #jobKey(long)} to also be set, and requires a CONFIGURATION item within the
     * batch that establishes model, provider, and systemPrompt — {@link #model(String)}, {@link
     * #provider(String)}, and {@link #systemPrompt(String)} must then be omitted.
     *
     * @param history the history items to append, in order. Must not be null; elements must not be
     *     null.
     * @return this builder for method chaining
     */
    CreateAgentInstanceCommandStep2 history(List<AgentInstanceHistoryItem> history);
  }
}
