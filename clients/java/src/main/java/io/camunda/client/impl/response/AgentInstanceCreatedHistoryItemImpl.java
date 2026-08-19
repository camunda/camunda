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
package io.camunda.client.impl.response;

import io.camunda.client.api.response.AgentInstanceCreatedHistoryItem;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

final class AgentInstanceCreatedHistoryItemImpl implements AgentInstanceCreatedHistoryItem {
  private final String historyItemId;
  private final long historyItemKey;
  private final boolean duplicate;

  AgentInstanceCreatedHistoryItemImpl(
      final io.camunda.client.protocol.rest.AgentInstanceCreatedHistoryItem item) {
    historyItemId = item.getHistoryItemId();
    historyItemKey = Long.parseLong(item.getHistoryItemKey());
    duplicate = Boolean.TRUE.equals(item.getIsDuplicate());
  }

  static List<AgentInstanceCreatedHistoryItem> toCreatedHistory(
      final List<io.camunda.client.protocol.rest.AgentInstanceCreatedHistoryItem> createdHistory) {
    if (createdHistory == null) {
      return Collections.emptyList();
    }
    return createdHistory.stream()
        .map(AgentInstanceCreatedHistoryItemImpl::new)
        .collect(Collectors.toList());
  }

  @Override
  public String getHistoryItemId() {
    return historyItemId;
  }

  @Override
  public long getHistoryItemKey() {
    return historyItemKey;
  }

  @Override
  public boolean isDuplicate() {
    return duplicate;
  }
}
