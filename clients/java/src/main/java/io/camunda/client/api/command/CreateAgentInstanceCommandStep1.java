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
 * <p>Usage example, establishing model/provider/systemPrompt through a CONFIGURATION history item:
 *
 * <pre>
 *   CreateAgentInstanceResponse response = camundaClient
 *       .newCreateAgentInstanceCommand()
 *       .elementInstanceKey(2251799813685248L)
 *       .jobKey(jobKey)
 *       .jobLease(jobLease)
 *       .history(List.of(
 *           new AgentInstanceHistoryItem()
 *               .historyItemId("item-0")
 *               .loopIteration(1)
 *               .role(AgentInstanceHistoryRole.CONFIGURATION)
 *               .content(List.of(AgentInstanceHistoryContent.text("configuration")))
 *               .producedAt(OffsetDateTime.now())
 *               .model("gpt-4o")
 *               .provider("openai")
 *               .systemPrompt(List.of(AgentInstanceHistoryContent.text("You are a helpful assistant.")))))
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

  interface CreateAgentInstanceCommandStep2 {

    /**
     * Sets the job key of the currently active job during which this creation was produced. Always
     * required — a creation must always be attributed to the active job that produced it.
     *
     * @param jobKey the key of the active job. Must be greater than 0.
     * @return the next step of the builder
     */
    CreateAgentInstanceCommandStep3 jobKey(long jobKey);
  }

  interface CreateAgentInstanceCommandStep3 {

    /**
     * Sets the opaque job lease token received from the job activation response. Always required —
     * agent-instance creation requires the job to have been activated with leasing enabled (see the
     * job worker's {@code withLease} option). The lease disambiguates this activation from any
     * other activation of the same job: if the job is later retried, history items submitted under
     * a superseded lease are discarded rather than committed.
     *
     * @param jobLease the lease token. Must not be null or blank.
     * @return the next step of the builder
     */
    CreateAgentInstanceCommandStep4 jobLease(String jobLease);
  }

  interface CreateAgentInstanceCommandStep4 {

    /**
     * Sets the batch of conversation history items to append to the agent instance at creation.
     * Always required — a CONFIGURATION item within the batch establishing model, provider, and
     * systemPrompt is the only way to create an agent instance.
     *
     * @param history the history items to append, in order. Must not be null or empty; elements
     *     must not be null.
     * @return the next step of the builder
     */
    CreateAgentInstanceCommandStep5 history(List<AgentInstanceHistoryItem> history);
  }

  interface CreateAgentInstanceCommandStep5 extends FinalCommandStep<CreateAgentInstanceResponse> {}
}
