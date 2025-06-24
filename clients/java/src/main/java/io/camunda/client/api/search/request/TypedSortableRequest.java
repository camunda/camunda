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
package io.camunda.client.api.search.request;

import java.util.function.Consumer;

public interface TypedSortableRequest<S, SELF extends TypedSortableRequest<S, SELF>> {

  /**
   * Sets the sorting the returned entities should be sorted by.
   *
   * @param value the sort options
   * @return the builder for the search request
   */
  SELF sort(final S value);

  /**
   * Provides a fluent builder to provide sorting options the returned entities should sorted by
   *
   * @param fn consumer to create the sort options
   * @return the builder for the search request
   */
  SELF sort(final Consumer<S> fn);

  interface SearchRequestSort<S extends SearchRequestSort<S>> {

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
