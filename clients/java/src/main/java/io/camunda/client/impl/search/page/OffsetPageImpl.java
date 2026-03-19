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

import io.camunda.client.api.search.page.OffsetPage;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.OffsetPagination;

/** Implementation of {@link OffsetPage} supporting offset-based pagination. */
public class OffsetPageImpl extends TypedSearchRequestPropertyProvider<OffsetPagination>
    implements OffsetPage {

  private final OffsetPagination page;

  public OffsetPageImpl() {
    page = new OffsetPagination();
  }

  @Override
  public OffsetPage from(final Integer value) {
    page.setFrom(value);
    return this;
  }

  @Override
  public OffsetPage limit(final Integer value) {
    page.setLimit(value);
    return this;
  }

  @Override
  public OffsetPagination getSearchRequestProperty() {
    return page;
  }
}
