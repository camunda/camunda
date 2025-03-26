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

import io.camunda.client.api.search.query.TypedSearchRequest.SearchRequestFilter;
import io.camunda.client.api.search.response.IncidentErrorType;
import io.camunda.client.api.search.response.IncidentState;

public interface IncidentFilter extends SearchRequestFilter {

  /**
   * Filters incidents by the specified key.
   *
   * @param value the key of the incidents
   * @return the updated filter
   */
  IncidentFilter incidentKey(final Long value);

  /**
   * Filters incidents by the process definition key.
   *
   * @param value the key of the process definition
   * @return the updated filter
   */
  IncidentFilter processDefinitionKey(final Long value);

  /**
   * Filters incidents by the process definition id.
   *
   * @param value the bpmn process id of the process definition
   * @return the updated filter
   */
  IncidentFilter processDefinitionId(final String value);

  /**
   * Filters incidents by the process instance key.
   *
   * @param value the key of the process instance
   * @return the updated filter
   */
  IncidentFilter processInstanceKey(final Long value);

  /**
   * Filters incidents by error type.
   *
   * @param errorType the error type of incident
   * @return the updated filter
   */
  IncidentFilter errorType(final IncidentErrorType errorType);

  /**
   * Filters incidents by error message.
   *
   * @param errorMessage the message of incident
   * @return the updated filter
   */
  IncidentFilter errorMessage(final String errorMessage);

  /**
   * Filters incidents by the flow node id.
   *
   * @param value the id of flow node id.
   * @return the updated filter
   */
  IncidentFilter flowNodeId(final String value);

  /**
   * Filters incidents by the flow node instance key.
   *
   * @param value the key of flow node instance.
   * @return the updated filter
   */
  IncidentFilter flowNodeInstanceKey(final Long value);

  /**
   * Filters incidents by creation time of incident.
   *
   * @param creationTime the creation time of incident
   * @return the updated filter
   */
  IncidentFilter creationTime(final String creationTime);

  /**
   * Filters incidents by the state of incident.
   *
   * @param value the state of incident
   * @return the updated filter
   */
  IncidentFilter state(final IncidentState value);

  /**
   * Filters incidents by job key.
   *
   * @param value the key of the job.
   * @return the updated filter
   */
  IncidentFilter jobKey(final Long value);

  /**
   * Filters incidents by tenant id.
   *
   * @param value the id of tenant
   * @return the updated filter
   */
  IncidentFilter tenantId(final String value);
}
