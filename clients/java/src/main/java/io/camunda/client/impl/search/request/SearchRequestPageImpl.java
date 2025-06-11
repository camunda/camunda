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
package io.camunda.client.impl.search.request;

import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.protocol.rest.SearchQueryPageRequest;
import io.camunda.client.protocol.rest.SearchQuerySortItem;
import java.util.List;
import java.util.stream.Collectors;

public class SearchRequestPageImpl
    extends TypedSearchRequestPropertyProvider<SearchQueryPageRequest>
    implements SearchRequestPage {

  private final SearchQueryPageRequest page;

  public SearchRequestPageImpl() {
    page = new SearchQueryPageRequest();
  }

  @Override
  public SearchRequestPage from(final Integer value) {
    page.setFrom(value);
    return this;
  }

  @Override
  public SearchRequestPage limit(final Integer value) {
    page.setLimit(value);
    return this;
  }

  @Override
  public SearchRequestPage searchBefore(final List<Object> values) {
    page.setSearchBefore(toSortItems(values));
    return this;
  }

  @Override
  public SearchRequestPage searchAfter(final List<Object> values) {
    page.setSearchAfter(toSortItems(values));
    return this;
  }

  @Override
  public SearchQueryPageRequest getSearchRequestProperty() {
    return page;
  }

  private static List<SearchQuerySortItem> toSortItems(final List<Object> sortObjects) {
    if (sortObjects == null) {
      return null;
    }

    return sortObjects.stream().map(SearchRequestPageImpl::toSortItem).collect(Collectors.toList());
  }

  private static SearchQuerySortItem toSortItem(final Object sortObject) {
    if (sortObject == null) {
      return null;
    }

    final String value = sortObject.toString();
    final String type;
    if (sortObject instanceof Boolean) {
      type = "boolean";
    } else if (sortObject instanceof Integer) {
      type = "int32";
    } else if (sortObject instanceof Long) {
      type = "int64";
    } else if (sortObject instanceof Float) {
      type = "float";
    } else if (sortObject instanceof Double) {
      type = "double";
    } else if (sortObject instanceof String) {
      type = "string";
    } else {
      throw new RuntimeException(
          String.format("Unsupported sort object class: %s", sortObject.getClass().getName()));
    }
    final SearchQuerySortItem sortItem = new SearchQuerySortItem();
    sortItem.setValue(value);
    sortItem.setType(type);

    return sortItem;
  }
}
