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
package io.camunda.zeebe.client.api.search.filter;

import io.camunda.zeebe.client.api.search.query.TypedSearchQueryRequest.SearchRequestFilter;

public interface IncidentFilter extends SearchRequestFilter {

  IncidentFilter key(final Long value);

  IncidentFilter processDefinitionKey(final Long value);

  IncidentFilter processInstanceKey(final Long value);

  IncidentFilter type(final String type);

  IncidentFilter flowNodeId(final String value);

  IncidentFilter flowNodeInstanceId(final String value);

  IncidentFilter creationTime(final String value);

  IncidentFilter state(final String value);

  IncidentFilter jobKey(final Long value);

  IncidentFilter tenantId(final String value);

  IncidentFilter hasActiveOperation(final Boolean value);
}
