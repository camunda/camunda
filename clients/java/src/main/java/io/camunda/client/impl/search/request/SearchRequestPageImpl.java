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
import java.util.List;

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
    page.setSearchBefore(values);
    return this;
  }

  @Override
  public SearchRequestPage searchAfter(final List<Object> values) {
    page.setSearchAfter(values);
    return this;
  }

  @Override
  public SearchQueryPageRequest getSearchRequestProperty() {
    return page;
  }
}
