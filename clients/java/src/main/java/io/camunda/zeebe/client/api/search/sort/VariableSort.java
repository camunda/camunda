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

public interface VariableSort extends SearchRequestSort<VariableSort> {

    /**
    * Sorts variables by the specified key.
    *
    * @param key the key of the variable
    * @return the updated sort
    */
    VariableSort variableKey(final Long key);

    /**
    * Sorts variables by the specified value.
    *
    * @param value the value of the variable
    * @return the updated sort
    */
    VariableSort value(final Object value);

    /**
    * Sorts variables by the specified name.
    *
    * @param name the name of the variable
    * @return the updated sort
    */
    VariableSort name(final String name);

    /**
    * Sorts variables by the specified scope key.
    * @param scopeKey
    * @return
    *
    * @param scopeKey the scope key of the variable
    * @return the updated sort
    */
    VariableSort scopeKey(final Long scopeKey);

    /**
    * Sorts variables by the specified process instance key.
    *
    * @param processInstanceKey the process instance key of the variable
    * @return the updated sort
    */
    VariableSort processInstanceKey(final Long processInstanceKey);

  /**
   * Sorts variables by the specified tenant id.
   *
   * @param tenantId
   * @return
   */
    VariableSort tenantId(final String tenantId);
}
