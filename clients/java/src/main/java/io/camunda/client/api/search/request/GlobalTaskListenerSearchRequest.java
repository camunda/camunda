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

import io.camunda.client.api.search.filter.GlobalTaskListenerFilter;
import io.camunda.client.api.search.page.AnyPage;
import io.camunda.client.api.search.response.GlobalTaskListener;
import io.camunda.client.api.search.sort.GlobalTaskListenerSort;

public interface GlobalTaskListenerSearchRequest
    extends TypedSearchRequest<
            GlobalTaskListenerFilter,
            GlobalTaskListenerSort,
            AnyPage,
            GlobalTaskListenerSearchRequest>,
        TypedPageableRequest<AnyPage, GlobalTaskListenerSearchRequest>,
        FinalSearchRequestStep<GlobalTaskListener> {}
