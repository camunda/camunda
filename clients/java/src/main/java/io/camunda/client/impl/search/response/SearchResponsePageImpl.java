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
  private final String searchBeforeCursor;
  private final String searchAfterCursor;

  public SearchResponsePageImpl(
      final long totalItems, final String searchBeforeCursor, final String searchAfterCursor) {
    this.totalItems = totalItems;
    this.searchBeforeCursor = searchBeforeCursor;
    this.searchAfterCursor = searchAfterCursor;
  }

  @Override
  public Long totalItems() {
    return totalItems;
  }

  @Override
  public String searchBeforeCursor() {
    return searchBeforeCursor;
  }

  @Override
  public String searchAfterCursor() {
    return searchAfterCursor;
  }
}
