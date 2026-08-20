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

/**
 * Represents a request to create a new agent instance.
 *
 * <p>Usage example:
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
 */
public interface CreateAgentInstanceCommandStep1 {

  /**
   * Sets the element instance key of the AHSP or AI Agent Task element instance.
   *
   * @param elementInstanceKey the key of the element instance. Must be greater than 0.
   * @return this builder for method chaining
   */
  CreateAgentInstanceCommandStep2 elementInstanceKey(long elementInstanceKey);

  interface CreateAgentInstanceCommandStep2 {

    /**
     * Sets the LLM model identifier for the agent instance.
     *
     * @param model the model identifier (for example, gpt-4o). Must not be null or empty.
     * @return this builder for method chaining
     */
    CreateAgentInstanceCommandStep3 model(String model);
  }

  interface CreateAgentInstanceCommandStep3 {

    /**
     * Sets the LLM provider for the agent instance.
     *
     * @param provider the provider name (for example, openai). Must not be null or empty.
     * @return this builder for method chaining
     */
    CreateAgentInstanceCommandStep4 provider(String provider);
  }

  interface CreateAgentInstanceCommandStep4 {

    /**
     * Sets the system prompt for the agent instance.
     *
     * @param systemPrompt the system prompt text. Must not be null or empty.
     * @return this builder for method chaining
     */
    CreateAgentInstanceCommandStep5 systemPrompt(String systemPrompt);
  }

  interface CreateAgentInstanceCommandStep5
      extends CreateAgentInstanceCommandStep1,
          CreateAgentInstanceCommandStep2,
          CreateAgentInstanceCommandStep3,
          CreateAgentInstanceCommandStep4,
          FinalCommandStep<CreateAgentInstanceResponse> {

    /**
     * Sets the maximum number of tokens the agent instance may use. Defaults to -1 (no limit).
     *
     * @param maxTokens the token limit. Use -1 for no limit.
     * @return this builder for method chaining
     */
    CreateAgentInstanceCommandStep5 maxTokens(long maxTokens);

    /**
     * Sets the maximum number of LLM model calls the agent instance may make. Defaults to -1 (no
     * limit).
     *
     * @param maxModelCalls the model-call limit. Use -1 for no limit.
     * @return this builder for method chaining
     */
    CreateAgentInstanceCommandStep5 maxModelCalls(int maxModelCalls);

    /**
     * Sets the maximum number of tool calls the agent instance may make. Defaults to -1 (no limit).
     *
     * @param maxToolCalls the tool-call limit. Use -1 for no limit.
     * @return this builder for method chaining
     */
    CreateAgentInstanceCommandStep5 maxToolCalls(int maxToolCalls);
  }
}
