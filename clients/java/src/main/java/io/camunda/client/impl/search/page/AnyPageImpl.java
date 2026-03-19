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
import io.camunda.client.api.search.page.CursorBackwardPage;
import io.camunda.client.api.search.page.CursorForwardPage;
import io.camunda.client.api.search.page.OffsetPage;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.SearchQueryPageRequest;

/**
 * Implementation of {@link AnyPage} supporting all pagination models.
 *
 * <p>Calling a direction method ({@code from}, {@code after}, {@code before}) locks the pagination
 * style by returning a lightweight view that only exposes the methods valid for that style, while
 * still mutating the same underlying {@link SearchQueryPageRequest}.
 */
public class AnyPageImpl extends TypedSearchRequestPropertyProvider<SearchQueryPageRequest>
    implements AnyPage {

  private final SearchQueryPageRequest page;

  public AnyPageImpl() {
    page = new SearchQueryPageRequest();
  }

  @Override
  public OffsetPage from(final Integer value) {
    page.setFrom(value);
    return new OffsetPageView(page);
  }

  @Override
  public AnyPage limit(final Integer value) {
    page.setLimit(value);
    return this;
  }

  @Override
  public CursorBackwardPage before(final String cursor) {
    page.before(cursor);
    return new CursorBackwardPageView(page);
  }

  @Override
  public CursorForwardPage after(final String cursor) {
    page.after(cursor);
    return new CursorForwardPageView(page);
  }

  @Override
  protected SearchQueryPageRequest getSearchRequestProperty() {
    return page;
  }

  /**
   * Lightweight adapter exposing only offset-based pagination methods while mutating the shared
   * page request.
   */
  private static final class OffsetPageView implements OffsetPage {
    private final SearchQueryPageRequest page;

    OffsetPageView(final SearchQueryPageRequest page) {
      this.page = page;
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
  }

  /**
   * Lightweight adapter exposing only cursor-forward pagination methods while mutating the shared
   * page request.
   */
  private static final class CursorForwardPageView implements CursorForwardPage {
    private final SearchQueryPageRequest page;

    CursorForwardPageView(final SearchQueryPageRequest page) {
      this.page = page;
    }

    @Override
    public CursorForwardPage after(final String cursor) {
      page.after(cursor);
      return this;
    }

    @Override
    public CursorForwardPage limit(final Integer value) {
      page.setLimit(value);
      return this;
    }
  }

  /**
   * Lightweight adapter exposing only cursor-backward pagination methods while mutating the shared
   * page request.
   */
  private static final class CursorBackwardPageView implements CursorBackwardPage {
    private final SearchQueryPageRequest page;

    CursorBackwardPageView(final SearchQueryPageRequest page) {
      this.page = page;
    }

    @Override
    public CursorBackwardPage before(final String cursor) {
      page.before(cursor);
      return this;
    }

    @Override
    public CursorBackwardPage limit(final Integer value) {
      page.setLimit(value);
      return this;
    }
  }
}
