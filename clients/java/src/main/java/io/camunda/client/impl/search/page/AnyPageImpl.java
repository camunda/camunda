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
package io.camunda.client.impl.search.page;

import io.camunda.client.api.search.page.AnyPage;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.SearchQueryPageRequest;

/** Implementation of {@link AnyPage} supporting all pagination models. */
public class AnyPageImpl extends TypedSearchRequestPropertyProvider<SearchQueryPageRequest>
    implements AnyPage {

  private final SearchQueryPageRequest page;

  public AnyPageImpl() {
    page = new SearchQueryPageRequest();
  }

  @Override
  public AnyPage from(final Integer value) {
    page.setFrom(value);
    return this;
  }

  @Override
  public AnyPage limit(final Integer value) {
    page.setLimit(value);
    return this;
  }

  @Override
  public AnyPage before(final String cursor) {
    page.before(cursor);
    return this;
  }

  @Override
  public AnyPage after(final String cursor) {
    page.after(cursor);
    return this;
  }

  @Override
  public SearchQueryPageRequest getSearchRequestProperty() {
    return page;
  }
}
