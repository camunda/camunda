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
 * <p>Use this for search endpoints that support any pagination model. Note that only one pagination
 * style should be used at a time:
 *
 * <ul>
 *   <li><b>Limit only:</b> {@code limit(10)}
 *   <li><b>Offset-based:</b> {@code from(20).limit(10)}
 *   <li><b>Cursor forward:</b> {@code after("cursor").limit(10)}
 *   <li><b>Cursor backward:</b> {@code before("cursor").limit(10)}
 * </ul>
 *
 * <p>Mixing pagination styles (e.g., {@code from(10).after("cursor")}) will result in a runtime
 * error.
 */
public interface AnyPage extends SearchRequestPage<AnyPage> {

  /** Start the page from the given offset (0-based index). */
  AnyPage from(final Integer value);

  /** Limit the number of returned entities. */
  AnyPage limit(final Integer value);

  /**
   * Get previous page before the cursor. Use the {@code startCursor} value from the previous
   * response to fetch the previous page of results.
   */
  AnyPage before(final String cursor);

  /**
   * Get next page after the cursor. Use the {@code endCursor} value from the previous response to
   * fetch the next page of results.
   */
  AnyPage after(final String cursor);
}
