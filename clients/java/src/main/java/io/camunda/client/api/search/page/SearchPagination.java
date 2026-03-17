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
package io.camunda.client.api.search.page;

/**
 * Base interface for pagination models in search requests.
 *
 * <p>This interface is similar to {@link
 * io.camunda.client.api.search.request.TypedSortableRequest.SearchRequestSort} and enforces
 * type-safe pagination models for search endpoints.
 *
 * <p>Concrete pagination models:
 *
 * <ul>
 *   <li>{@link LimitPage} - Only {@code limit}
 *   <li>{@link OffsetPage} - {@code from} + {@code limit}
 *   <li>{@link CursorForwardPage} - {@code after} + {@code limit}
 *   <li>{@link CursorBackwardPage} - {@code before} + {@code limit}
 *   <li>{@link AnyPage} - All pagination models (for general search endpoints)
 * </ul>
 *
 * @param <P> the concrete pagination model type
 */
public interface SearchPagination<P extends SearchPagination<P>> {}
