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
package io.camunda.client.api.statistics.request;

import io.camunda.client.api.statistics.filter.ProcessDefinitionInstanceVersionStatisticsFilter;
import io.camunda.client.api.statistics.sort.ProcessDefinitionInstanceStatisticsSort;
import io.camunda.client.api.statistics.sort.ProcessDefinitionInstanceVersionStatisticsSort;
import io.camunda.client.impl.statistics.filter.ProcessDefinitionInstanceVersionStatisticsFilterImpl;
import io.camunda.client.impl.statistics.sort.ProcessDefinitionInstanceStatisticsSortImpl;
import io.camunda.client.impl.statistics.sort.ProcessDefinitionInstanceVersionStatisticsSortImpl;
import java.util.function.Consumer;

public final class StatisticsRequestBuilders {

  public static ProcessDefinitionInstanceVersionStatisticsFilter
      processDefinitionInstanceVersionStatisticsFilter(
          final Consumer<ProcessDefinitionInstanceVersionStatisticsFilter> fn) {
    final ProcessDefinitionInstanceVersionStatisticsFilter filter =
        new ProcessDefinitionInstanceVersionStatisticsFilterImpl();
    fn.accept(filter);
    return filter;
  }

  public static ProcessDefinitionInstanceStatisticsSort processDefinitionInstanceStatisticsSort(
      final Consumer<ProcessDefinitionInstanceStatisticsSort> fn) {
    final ProcessDefinitionInstanceStatisticsSort sort =
        new ProcessDefinitionInstanceStatisticsSortImpl();
    fn.accept(sort);
    return sort;
  }

  public static ProcessDefinitionInstanceVersionStatisticsSort
      processDefinitionInstanceVersionStatisticsSort(
          final Consumer<ProcessDefinitionInstanceVersionStatisticsSort> fn) {
    final ProcessDefinitionInstanceVersionStatisticsSort sort =
        new ProcessDefinitionInstanceVersionStatisticsSortImpl();
    fn.accept(sort);
    return sort;
  }
}
