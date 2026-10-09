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
package io.camunda.client.api.search.request;

import io.camunda.client.api.search.filter.AgentDefinitionFilter;
import io.camunda.client.api.search.page.AnyPage;
import io.camunda.client.api.search.response.AgentDefinition;
import io.camunda.client.api.search.sort.AgentDefinitionSort;

public interface AgentDefinitionSearchRequest
    extends TypedSearchRequest<
            AgentDefinitionFilter, AgentDefinitionSort, AnyPage, AgentDefinitionSearchRequest>,
        TypedPageableRequest<AnyPage, AgentDefinitionSearchRequest>,
        FinalSearchRequestStep<AgentDefinition> {}
