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

import io.camunda.zeebe.client.api.search.QueryPage;
import io.camunda.zeebe.client.protocol.rest.SearchQueryPageRequest;
import java.util.Arrays;

public class QueryPageImpl extends TypedQueryProperty<SearchQueryPageRequest> implements QueryPage {

  private final SearchQueryPageRequest page;

  public QueryPageImpl() {
    page = new SearchQueryPageRequest();
  }

  @Override
  public QueryPage from(final Integer value) {
    page.setFrom(value);
    return this;
  }

  @Override
  public QueryPage size(final Integer value) {
    page.setSize(value);
    return this;
  }

  @Override
  public QueryPage searchBefore(final Object[] values) {
    page.setSearchBefore(Arrays.asList(values));
    return this;
  }

  @Override
  public QueryPage searchAfter(final Object[] values) {
    page.setSearchAfter(Arrays.asList(values));
    return this;
  }

  @Override
  protected SearchQueryPageRequest getQueryProperty() {
    return page;
  }
}
