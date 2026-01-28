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
package io.camunda.client.api.statistics.filter;

import io.camunda.client.api.statistics.request.StatisticsRequest.StatisticsRequestFilter;
import java.util.function.Consumer;

public interface IncidentProcessInstanceStatisticsByDefinitionFilter
    extends StatisticsRequestFilter {

  /**
   * Restricts the statistics to active process instances that have incidents with the given error
   * hash code.
   *
   * <p>This filter is mandatory for this statistic and defines the incident error for which process
   * instance statistics are calculated.
   *
   * @param value the error hash code identifying the incident error
   * @return the updated filter
   */
  IncidentProcessInstanceStatisticsByDefinitionFilter errorHashCode(final Integer value);

  /**
   * Restricts the statistics to active process instances that have incidents with the given error
   * hash code, provided via a consumer.
   *
   * <p>This filter is mandatory for this statistic and defines the incident error for which process
   * instance statistics are calculated.
   *
   * @param fn consumer used to supply the error hash code
   * @return the updated filter
   */
  IncidentProcessInstanceStatisticsByDefinitionFilter errorHashCode(final Consumer<Integer> fn);
}
