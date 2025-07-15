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
package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.response.SearchResponsePage;

public class SearchResponsePageImpl implements SearchResponsePage {

  private final long totalItems;
  private final boolean hasMoreTotalItems;
  private final String startCursor;
  private final String endCursor;

  public SearchResponsePageImpl(
      final Long totalItems, final String startCursor, final String endCursor) {
    this(totalItems, false, startCursor, endCursor);
  }

  public SearchResponsePageImpl(
      final Long totalItems,
      final Boolean hasMoreTotalItems,
      final String startCursor,
      final String endCursor) {
    this.totalItems = totalItems != null ? totalItems : 0L;
    this.hasMoreTotalItems = Boolean.TRUE.equals(hasMoreTotalItems);
    this.startCursor = startCursor;
    this.endCursor = endCursor;
  }

  @Override
  public Long totalItems() {
    return totalItems;
  }

  @Override
  public Boolean hasMoreTotalItems() {
    return hasMoreTotalItems;
  }

  @Override
  public String startCursor() {
    return startCursor;
  }

  @Override
  public String endCursor() {
    return endCursor;
  }
}
