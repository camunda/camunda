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
package io.camunda.client.api.search.filter;

import io.camunda.client.api.search.enums.AgentInstanceHistoryCommitStatus;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.filter.builder.AgentInstanceHistoryCommitStatusProperty;
import io.camunda.client.api.search.filter.builder.AgentInstanceHistoryRoleProperty;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.util.function.Consumer;

public interface AgentInstanceHistoryFilter extends SearchRequestFilter {

  AgentInstanceHistoryFilter historyItemKey(long value);

  AgentInstanceHistoryFilter historyItemKey(Consumer<BasicLongProperty> fn);

  AgentInstanceHistoryFilter role(AgentInstanceHistoryRole value);

  AgentInstanceHistoryFilter role(Consumer<AgentInstanceHistoryRoleProperty> fn);

  AgentInstanceHistoryFilter elementInstanceKey(long value);

  AgentInstanceHistoryFilter elementInstanceKey(Consumer<BasicLongProperty> fn);

  AgentInstanceHistoryFilter jobKey(long value);

  AgentInstanceHistoryFilter jobKey(Consumer<BasicLongProperty> fn);

  AgentInstanceHistoryFilter iteration(int value);

  AgentInstanceHistoryFilter iteration(Consumer<IntegerProperty> fn);

  AgentInstanceHistoryFilter commitStatus(AgentInstanceHistoryCommitStatus value);

  AgentInstanceHistoryFilter commitStatus(Consumer<AgentInstanceHistoryCommitStatusProperty> fn);

  AgentInstanceHistoryFilter producedAt(Consumer<DateTimeProperty> fn);
}
