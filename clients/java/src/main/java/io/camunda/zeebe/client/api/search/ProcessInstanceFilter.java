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
package io.camunda.zeebe.client.api.search;

import io.camunda.zeebe.client.api.search.TypedSearchQueryRequest.SearchRequestFilter;
import java.util.List;
import java.util.function.Consumer;

public interface ProcessInstanceFilter extends SearchRequestFilter {

  /** Filter by process instance keys. */
  ProcessInstanceFilter processInstanceKeys(final Long... values);

  /** Filter by process instance keys. */
  ProcessInstanceFilter processInstanceKeys(final List<Long> values);

  /** Filter by variable values. */
  ProcessInstanceFilter variable(final VariableValueFilter filter);

  /** Filter by variable values. */
  ProcessInstanceFilter variable(final Consumer<VariableValueFilter> fn);
}
