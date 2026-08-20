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

public interface ProcessDefinitionInstanceVersionStatisticsFilter extends StatisticsRequestFilter {

  /**
   * Filters by process definition ID. This filter is mandatory for this statistic and defines the
   * process definition for which version statistics are calculated.
   *
   * @param value the ID of the process definition
   * @return the updated filter
   */
  ProcessDefinitionInstanceVersionStatisticsFilter processDefinitionId(final String value);

  /**
   * Filters by process definition ID, provided via a consumer. This filter is mandatory for this
   * statistic and defines the process definition for which version statistics are calculated.
   *
   * @param fn consumer used to supply the process definition ID
   * @return the updated filter
   */
  ProcessDefinitionInstanceVersionStatisticsFilter processDefinitionId(final Consumer<String> fn);

  /**
   * Filters process definition by tenant id.
   *
   * @param value the id of tenant
   * @return the updated filter
   */
  ProcessDefinitionInstanceVersionStatisticsFilter tenantId(final String value);

  /**
   * Filters process definition by tenant id using consumer.
   *
   * @param fn consumer to create the string property
   * @return the updated filter
   */
  ProcessDefinitionInstanceVersionStatisticsFilter tenantId(final Consumer<String> fn);
}
