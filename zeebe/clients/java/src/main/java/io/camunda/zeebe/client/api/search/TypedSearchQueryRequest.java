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
package io.camunda.zeebe.client.api.search;

import io.camunda.zeebe.client.api.search.TypedSearchQueryRequest.SearchRequestFilter;
import io.camunda.zeebe.client.api.search.TypedSearchQueryRequest.SearchRequestSort;
import java.util.function.Consumer;

public interface TypedSearchQueryRequest<
    F extends SearchRequestFilter,
    S extends SearchRequestSort<S>,
    SELF extends TypedSearchQueryRequest<F, S, SELF>> {

  SELF filter(final F value);

  SELF filter(final Consumer<F> fn);

  SELF sort(final S value);

  SELF sort(final Consumer<S> fn);

  SELF page(final SearchRequestPage value);

  SELF page(final Consumer<SearchRequestPage> fn);

  public static interface SearchRequestFilter {}

  public static interface SearchRequestSort<S extends SearchRequestSort<S>> {

    S asc();

    S desc();
  }
}
