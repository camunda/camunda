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
 * All pagination models combined, supporting offset, limit, and cursor-based (forward/backward)
 * pagination.
 *
 * <p>Use this for search endpoints that support any pagination model. Calling a direction method
 * ({@code from}, {@code after}, {@code before}) locks the pagination style by returning a
 * style-specific interface, so incompatible methods cannot be chained:
 *
 * <ul>
 *   <li><b>Limit only:</b> {@code limit(10)} — stays on {@code AnyPage}
 *   <li><b>Offset-based:</b> {@code from(20).limit(10)} — returns {@link OffsetPage}
 *   <li><b>Cursor forward:</b> {@code after("cursor").limit(10)} — returns {@link
 *       CursorForwardPage}
 *   <li><b>Cursor backward:</b> {@code before("cursor").limit(10)} — returns {@link
 *       CursorBackwardPage}
 * </ul>
 *
 * <p>Mixing pagination styles (e.g., {@code from(10).after("cursor")}) is prevented at compile-time
 * because the returned interface does not expose the incompatible method.
 */
public interface AnyPage extends SearchPagination<AnyPage> {

  /**
   * Start the page from the given offset (0-based index). Locks pagination to offset-based,
   * returning an {@link OffsetPage} that only exposes {@code from} and {@code limit}.
   */
  OffsetPage from(final Integer value);

  /** Limit the number of returned entities. */
  AnyPage limit(final Integer value);

  /**
   * Get previous page before the cursor. Locks pagination to cursor-backward, returning a {@link
   * CursorBackwardPage} that only exposes {@code before} and {@code limit}.
   */
  CursorBackwardPage before(final String cursor);

  /**
   * Get next page after the cursor. Locks pagination to cursor-forward, returning a {@link
   * CursorForwardPage} that only exposes {@code after} and {@code limit}.
   */
  CursorForwardPage after(final String cursor);
}
