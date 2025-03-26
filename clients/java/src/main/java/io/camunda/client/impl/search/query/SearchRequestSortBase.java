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
package io.camunda.client.impl.search.query;

import io.camunda.client.impl.search.SearchRequestSort;
import io.camunda.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.SortOrderEnum;
import java.util.ArrayList;
import java.util.List;

public abstract class SearchRequestSortBase<T>
    extends TypedSearchRequestPropertyProvider<List<SearchRequestSort>> {

  private final List<SearchRequestSort> sorting = new ArrayList<>();
  private SearchRequestSort current;

  protected T field(final String value) {
    current = new SearchRequestSort();
    current.field(value);
    return self();
  }

  protected T order(final SortOrderEnum order) {
    if (current != null) {
      current.order(order);
      sorting.add(current);
      current = null;
    }
    return self();
  }

  public T asc() {
    return order(SortOrderEnum.ASC);
  }

  public T desc() {
    return order(SortOrderEnum.DESC);
  }

  protected abstract T self();

  @Override
  protected List<SearchRequestSort> getSearchRequestProperty() {
    return sorting;
  }
}
