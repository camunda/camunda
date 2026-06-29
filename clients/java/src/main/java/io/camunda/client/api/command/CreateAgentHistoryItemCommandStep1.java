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
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import java.time.OffsetDateTime;
import java.util.List;

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
 *       .role(AgentInstanceHistoryRole.USER)
 *       .content(List.of(AgentInstanceHistoryContent.text("Hello!")))
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
    CreateAgentHistoryItemCommandStep4 role(AgentInstanceHistoryRole role);
  }

  interface CreateAgentHistoryItemCommandStep4 {

    /**
     * Sets the content blocks of this history item.
     *
     * @param content the list of content blocks. Must not be null; may be empty.
     * @return this builder for method chaining
     */
    CreateAgentHistoryItemCommandStep5 content(List<AgentInstanceHistoryContent> content);
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
    CreateAgentHistoryItemFinalCommandStep toolCalls(List<AgentInstanceHistoryToolCall> toolCalls);

    /**
     * Sets per-call token and latency metrics. Present on ASSISTANT items only.
     *
     * @param metrics the metrics. May be null.
     * @return this builder for method chaining
     */
    CreateAgentHistoryItemFinalCommandStep metrics(AgentInstanceHistoryMetrics metrics);
  }
}
