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
 *       .jobKey(jobKey)
 *       .jobLease(jobLease)
 *       .history(List.of(
 *           new AgentInstanceHistoryItem()
 *               .historyItemId("item-1")
 *               .loopIteration(1)
 *               .role(AgentInstanceHistoryRole.ASSISTANT)
 *               .content(List.of(AgentInstanceHistoryContent.text("Looking that up now.")))
 *               .producedAt(OffsetDateTime.now())))
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

  interface UpdateAgentInstanceCommandStep2 {

    /**
     * Sets the new status of the agent instance.
     *
     * @param status the new status; see {@link AgentInstanceUpdateStatus} for available values
     * @return this builder for method chaining
     */
    UpdateAgentInstanceCommandStep2 status(AgentInstanceUpdateStatus status);

    /**
     * Sets the job key of the currently active job during which this update was produced. Always
     * required — an update must always be attributed to the active job that produced it.
     *
     * @param jobKey the key of the active job. Must be greater than 0.
     * @return the next step of the builder
     */
    UpdateAgentInstanceCommandStep3 jobKey(long jobKey);
  }

  interface UpdateAgentInstanceCommandStep3 {

    /**
     * Sets the opaque job lease token received from the job activation response. Always required —
     * agent-instance updates require the job to have been activated with leasing enabled (see the
     * job worker's {@code withLease} option). The lease disambiguates this activation from any
     * other activation of the same job: if the job is later retried, history items submitted under
     * a superseded lease are discarded rather than committed.
     *
     * @param jobLease the lease token. Must not be null or blank.
     * @return the next step of the builder
     */
    UpdateAgentInstanceCommandStep4 jobLease(String jobLease);
  }

  interface UpdateAgentInstanceCommandStep4 extends FinalCommandStep<UpdateAgentInstanceResponse> {

    /**
     * Replaces the full batch of conversation history items to append to the agent instance.
     *
     * @param history the history items to append, in order. Must not be null; elements must not be
     *     null.
     * @return this builder for method chaining
     */
    UpdateAgentInstanceCommandStep4 history(List<AgentInstanceHistoryItem> history);
  }
}
