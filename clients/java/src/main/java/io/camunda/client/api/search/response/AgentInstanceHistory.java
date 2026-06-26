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
package io.camunda.client.api.search.response;

import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryMetrics;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.search.enums.AgentInstanceHistoryCommitStatus;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import java.time.OffsetDateTime;
import java.util.List;

public interface AgentInstanceHistory {

  long getHistoryItemKey();

  long getAgentInstanceKey();

  long getElementInstanceKey();

  long getJobKey();

  String getJobLease();

  Integer getIteration();

  AgentInstanceHistoryRole getRole();

  List<AgentInstanceHistoryContent> getContent();

  List<AgentInstanceHistoryToolCall> getToolCalls();

  AgentInstanceHistoryMetrics getMetrics();

  AgentInstanceHistoryCommitStatus getCommitStatus();

  OffsetDateTime getProducedAt();
}
