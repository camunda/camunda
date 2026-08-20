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
package io.camunda.client.impl.search.sort;

import io.camunda.client.api.search.sort.VariableSort;
import io.camunda.client.impl.search.request.SearchRequestSortBase;

public class VariableSortImpl extends SearchRequestSortBase<VariableSort> implements VariableSort {

  @Override
  public VariableSort variableKey() {
    return field("key");
  }

  @Override
  public VariableSort value() {
    return field("value");
  }

  @Override
  public VariableSort name() {
    return field("name");
  }

  @Override
  public VariableSort scopeKey() {
    return field("scopeKey");
  }

  @Override
  public VariableSort processInstanceKey() {
    return field("processInstanceKey");
  }

  @Override
  public VariableSort tenantId() {
    return field("tenantId");
  }

  @Override
  protected VariableSort self() {
    return this;
  }
}
