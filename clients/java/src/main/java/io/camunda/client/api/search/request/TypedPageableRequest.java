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

import io.camunda.client.api.search.page.SearchPagination;
import java.util.function.Consumer;

/**
 * Interface for typed pagination support in search requests.
 *
 * <p>This interface enforces type-safe pagination models for search endpoints, similar to how
 * {@link TypedSortableRequest} enforces sort type safety.
 *
 * @param <P> the pagination model type (e.g., {@link
 *     io.camunda.client.api.search.page.CursorForwardPage}, {@link
 *     io.camunda.client.api.search.page.AnyPage})
 * @param <SELF> the concrete request type for fluent method chaining
 */
public interface TypedPageableRequest<
    P extends SearchPagination<P>, SELF extends TypedPageableRequest<P, SELF>> {

  /**
   * Support for pagination.
   *
   * @param value the next page
   * @return the builder for the search request
   */
  SELF page(final P value);

  /**
   * Provides a fluent builder to support pagination.
   *
   * @param fn consumer to support pagination
   * @return the builder for the search request
   */
  SELF page(final Consumer<P> fn);
}
