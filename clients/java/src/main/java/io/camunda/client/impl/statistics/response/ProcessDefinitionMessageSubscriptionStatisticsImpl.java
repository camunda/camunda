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
package io.camunda.client.impl.statistics.response;

import io.camunda.client.api.statistics.response.ProcessDefinitionMessageSubscriptionStatistics;
import io.camunda.client.protocol.rest.ProcessDefinitionMessageSubscriptionStatisticsResult;
import java.util.List;

public class ProcessDefinitionMessageSubscriptionStatisticsImpl
    implements ProcessDefinitionMessageSubscriptionStatistics {
  private final List<ProcessDefinitionMessageSubscriptionStatisticsResult> items;
  private final String endCursor;

  public ProcessDefinitionMessageSubscriptionStatisticsImpl(
      final List<ProcessDefinitionMessageSubscriptionStatisticsResult> items,
      final String endCursor) {
    this.items = items;
    this.endCursor = endCursor;
  }

  @Override
  public List<ProcessDefinitionMessageSubscriptionStatisticsResult> items() {
    return items;
  }

  @Override
  public String getEndCursor() {
    return endCursor;
  }

  @Override
  public String toString() {
    return "ProcessDefinitionMessageSubscriptionStatisticsImpl{"
        + "items="
        + items
        + ", endCursor='"
        + endCursor
        + '\''
        + '}';
  }
}
