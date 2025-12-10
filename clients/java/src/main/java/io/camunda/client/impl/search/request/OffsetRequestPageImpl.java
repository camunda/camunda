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

import io.camunda.client.api.search.request.OffsetRequestPage;
import io.camunda.client.protocol.rest.OffsetPagination;

public class OffsetRequestPageImpl extends TypedSearchRequestPropertyProvider<OffsetPagination>
    implements OffsetRequestPage {

  private final OffsetPagination page = new OffsetPagination();

  @Override
  public OffsetRequestPage from(final Integer value) {
    page.setFrom(value);
    return this;
  }

  @Override
  public OffsetRequestPage limit(final Integer value) {
    page.setLimit(value);
    return this;
  }

  @Override
  protected OffsetPagination getSearchRequestProperty() {
    return page;
  }
}
