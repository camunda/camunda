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
package io.camunda.zeebe.client.impl.search;

import io.camunda.zeebe.client.api.search.ProcessInstanceSort;
import io.camunda.zeebe.client.protocol.rest.SearchQuerySortRequest;

public class ProcessInstanceSortImpl extends TypedQueryProperty<SearchQuerySortRequest>
    implements ProcessInstanceSort {

  private SearchQuerySortRequest sort;

  public ProcessInstanceSortImpl() {
    sort = new SearchQuerySortRequest();
  }

  @Override
  public ProcessInstanceSort startDate() {
    sort.setField("startDate");
    return this;
  }

  @Override
  public ProcessInstanceSort endDate() {
    sort.setField("endDate");
    return this;
  }

  @Override
  public ProcessInstanceSort asc() {
    sort.setOrder("asc");
    return this;
  }

  @Override
  public ProcessInstanceSort desc() {
    sort.setOrder("desc");
    return this;
  }

  @Override
  protected SearchQuerySortRequest getQueryProperty() {
    return sort;
  }
}
