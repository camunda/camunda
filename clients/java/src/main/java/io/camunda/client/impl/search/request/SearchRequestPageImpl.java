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
import io.camunda.client.protocol.rest.CursorBackwardPagination;
import io.camunda.client.protocol.rest.CursorForwardPagination;
import io.camunda.client.protocol.rest.OffsetPagination;
import io.camunda.client.protocol.rest.SearchQueryPageRequest;

public class SearchRequestPageImpl
    extends TypedSearchRequestPropertyProvider<SearchQueryPageRequest>
    implements SearchRequestPage {

  private SearchQueryPageRequest page;

  @Override
  public SearchRequestPage from(final Integer value) {
    if (page == null) {
      page = new OffsetPagination().from(value);
    } else {
      page = new OffsetPagination().from(value).limit(getLimit(page));
    }
    return this;
  }

  @Override
  public SearchRequestPage limit(final Integer value) {
    if (page instanceof OffsetPagination) {
      ((OffsetPagination) page).setLimit(value);
    } else if (page instanceof CursorBackwardPagination) {
      ((CursorBackwardPagination) page).setLimit(value);
    } else if (page instanceof CursorForwardPagination) {
      ((CursorForwardPagination) page).setLimit(value);
    } else {
      page = new OffsetPagination().limit(value);
    }
    return this;
  }

  @Override
  public SearchRequestPage before(final String cursor) {
    if (page == null) {
      page = new CursorBackwardPagination().before(cursor);
    } else {
      page = new CursorBackwardPagination().before(cursor).limit(getLimit(page));
    }
    return this;
  }

  @Override
  public SearchRequestPage after(final String cursor) {
    if (page == null) {
      page = new CursorForwardPagination().after(cursor);
    } else {
      page = new CursorForwardPagination().after(cursor).limit(getLimit(page));
    }
    return this;
  }

  @Override
  public SearchQueryPageRequest getSearchRequestProperty() {
    return page;
  }

  private Integer getLimit(final SearchQueryPageRequest page) {
    if (page instanceof OffsetPagination) {
      return ((OffsetPagination) page).getLimit();
    } else if (page instanceof CursorBackwardPagination) {
      return ((CursorBackwardPagination) page).getLimit();
    } else if (page instanceof CursorForwardPagination) {
      return ((CursorForwardPagination) page).getLimit();
    }
    return null;
  }
}
