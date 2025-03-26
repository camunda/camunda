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

import io.camunda.client.api.search.request.TypedSearchRequest.SearchRequestFilter;

public interface VariableValueFilter extends SearchRequestFilter {

  /** Filter by variable name */
  VariableValueFilter name(final String value);

  /** Filter by variable value equal to value */
  VariableValueFilter eq(final Object value);

  /** Filter by variable value greater than value */
  VariableValueFilter gt(final Object value);

  /** Filter by variable value greater equals than value */
  VariableValueFilter gte(final Object value);

  /** Filter by variable value lower than value */
  VariableValueFilter lt(final Object value);

  /** Filter by variable value lower equals than value */
  VariableValueFilter lte(final Object value);
}
