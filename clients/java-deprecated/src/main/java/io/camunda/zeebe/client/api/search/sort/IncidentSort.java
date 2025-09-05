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
package io.camunda.zeebe.client.api.search.sort;

import io.camunda.zeebe.client.api.search.query.TypedSearchQueryRequest.SearchRequestSort;

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by {@link
 *     io.camunda.client.api.search.sort.IncidentSort}. Please see the <a
 *     href="https://docs.camunda.io/docs/8.8/apis-tools/migration-manuals/migrate-to-camunda-java-client/">Camunda
 *     Java Client migration guide</a>
 */
@Deprecated
public interface IncidentSort extends SearchRequestSort<IncidentSort> {

  IncidentSort key();

  IncidentSort processDefinitionKey();

  IncidentSort processInstanceKey();

  IncidentSort type();

  IncidentSort flowNodeId();

  IncidentSort flowNodeInstanceId();

  IncidentSort creationTime();

  IncidentSort state();

  IncidentSort jobKey();

  IncidentSort tenantId();
}
