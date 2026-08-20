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
package io.camunda.client.impl.statistics.sort;

import io.camunda.client.api.statistics.sort.ProcessDefinitionInstanceVersionStatisticsSort;
import io.camunda.client.impl.search.request.SearchRequestSortBase;

public class ProcessDefinitionInstanceVersionStatisticsSortImpl
    extends SearchRequestSortBase<ProcessDefinitionInstanceVersionStatisticsSort>
    implements ProcessDefinitionInstanceVersionStatisticsSort {

  @Override
  public ProcessDefinitionInstanceVersionStatisticsSort processDefinitionId() {
    return field("processDefinitionId");
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsSort processDefinitionKey() {
    return field("processDefinitionKey");
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsSort processDefinitionName() {
    return field("processDefinitionName");
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsSort processDefinitionVersion() {
    return field("processDefinitionVersion");
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsSort activeInstancesWithoutIncidentCount() {
    return field("activeInstancesWithoutIncidentCount");
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsSort activeInstancesWithIncidentCount() {
    return field("activeInstancesWithIncidentCount");
  }

  @Override
  protected ProcessDefinitionInstanceVersionStatisticsSort self() {
    return this;
  }
}
