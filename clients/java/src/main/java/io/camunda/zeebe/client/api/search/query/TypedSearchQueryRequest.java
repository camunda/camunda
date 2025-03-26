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
package io.camunda.zeebe.client.api.search.query;

import io.camunda.client.api.search.query.TypedSearchRequest;
import io.camunda.zeebe.client.api.search.SearchRequestPage;
import io.camunda.zeebe.client.api.search.query.TypedSearchQueryRequest.SearchRequestFilter;
import io.camunda.zeebe.client.api.search.query.TypedSearchQueryRequest.SearchRequestSort;
import java.util.function.Consumer;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link TypedSearchRequest}
 */
@Deprecated
public interface TypedSearchQueryRequest<
    F extends SearchRequestFilter,
    S extends SearchRequestSort<S>,
    SELF extends TypedSearchQueryRequest<F, S, SELF>> {

  /**
   * Sets the filter to be included in the search request.
   *
   * @param value the filter
   * @return the builder for the search request
   */
  SELF filter(final F value);

  /**
   * Provides a fluent builder to create a filter to be included in the search request.
   *
   * @param value consumer to create the filter
   * @return the builder for the search request
   */
  SELF filter(final Consumer<F> fn);

  /**
   * Sets the sorting the returned entities should be sorted by.
   *
   * @param value the sort options
   * @return the builder for the search request
   */
  SELF sort(final S value);

  /**
   * Provides a fluent builder to provide sorting options the returned entites should sorted by
   *
   * @param value consumer to create the sort options
   * @return the builder for the search request
   */
  SELF sort(final Consumer<S> fn);

  /**
   * Support for pagination.
   *
   * @param value the next page
   * @return the builder for the search request
   */
  SELF page(final SearchRequestPage value);

  /**
   * Provides a fluent builder to support pagination.
   *
   * @param value consumer to support pagination
   * @return the builder for the search request
   */
  SELF page(final Consumer<SearchRequestPage> fn);

  public static interface SearchRequestFilter {}

  public static interface SearchRequestSort<S extends SearchRequestSort<S>> {

    /**
     * Sort in ascending order
     *
     * @return the sort builder
     */
    S asc();

    /**
     * Sort in descending order
     *
     * @return the sort builder
     */
    S desc();
  }
}
