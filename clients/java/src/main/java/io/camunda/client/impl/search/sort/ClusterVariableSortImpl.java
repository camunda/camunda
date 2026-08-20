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

import io.camunda.client.api.search.sort.ClusterVariableSort;
import io.camunda.client.impl.search.request.SearchRequestSortBase;

public class ClusterVariableSortImpl extends SearchRequestSortBase<ClusterVariableSort>
    implements ClusterVariableSort {

  @Override
  public ClusterVariableSort value() {
    return field("value");
  }

  @Override
  public ClusterVariableSort name() {
    return field("name");
  }

  @Override
  public ClusterVariableSort scope() {
    return field("scope");
  }

  @Override
  public ClusterVariableSort tenantId() {
    return field("tenantId");
  }

  @Override
  protected ClusterVariableSort self() {
    return this;
  }
}
