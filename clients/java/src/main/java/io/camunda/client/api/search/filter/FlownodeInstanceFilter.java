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

import io.camunda.client.api.search.enums.FlowNodeInstanceState;
import io.camunda.client.api.search.enums.FlowNodeInstanceType;
import io.camunda.client.api.search.filter.builder.FlowNodeInstanceStateProperty;
import io.camunda.client.api.search.request.TypedSearchRequest.SearchRequestFilter;
import java.util.function.Consumer;

public interface FlownodeInstanceFilter extends SearchRequestFilter {

  /**
   * Filters flow node instances by the specified key.
   *
   * @param value the key of flow node instance
   * @return the updated filter
   */
  FlownodeInstanceFilter flowNodeInstanceKey(final long value);

  /**
   * Filters flow node instances by process definition key.
   *
   * @param value the process definition key of flow node instance
   * @return the updated filter
   */
  FlownodeInstanceFilter processDefinitionKey(final long value);

  /**
   * Filters flow node instances by bpmn process id.
   *
   * @param value the bpmn process id of flow node instance
   * @return the updated filter
   */
  FlownodeInstanceFilter processDefinitionId(final String value);

  /**
   * Filters flow node instances by process instance key.
   *
   * @param value the process instance key of flow node instance
   * @return the updated filter
   */
  FlownodeInstanceFilter processInstanceKey(final long value);

  /**
   * Filters flow node instances by flow node id.
   *
   * @param value the flow node id of flow node instance
   * @return the updated filter
   */
  FlownodeInstanceFilter flowNodeId(final String value);

  /**
   * Filters flow node instances by state.
   *
   * @param value the state of flow node instance
   * @return the updated filter
   */
  FlownodeInstanceFilter state(final FlowNodeInstanceState value);

  /** Filter by state using {@link FlowNodeInstanceStateProperty} consumer */
  FlownodeInstanceFilter state(final Consumer<FlowNodeInstanceStateProperty> fn);

  /**
   * Filters flow node instances by type.
   *
   * @param value the type of flow node instance
   * @return the updated filter
   */
  FlownodeInstanceFilter type(final FlowNodeInstanceType value);

  /**
   * Filters flow node instances by incident (has an incident)
   *
   * @param value has the flow node instance an incident
   * @return the updated filter
   */
  FlownodeInstanceFilter hasIncident(final boolean value);

  /**
   * Filters flow node instances by incident key.
   *
   * @param value the incident key for flow node instance
   * @return the updated filter
   */
  FlownodeInstanceFilter incidentKey(final long value);

  /**
   * Filters flow node instances by tenant id.
   *
   * @param value the tenant id for flow node instance
   * @return the updated filter
   */
  FlownodeInstanceFilter tenantId(final String value);
}
