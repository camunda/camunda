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
 * Cursor-based forward pagination supporting {@code after} cursor and {@code limit}.
 *
 * <p>Use this to navigate forward through result sets using the {@code endCursor} from the previous
 * response.
 */
public interface CursorForwardPage extends SearchPagination<CursorForwardPage> {

  /**
   * Get next page after the cursor. Use the {@code endCursor} value from the previous response to
   * fetch the next page of results.
   */
  CursorForwardPage after(final String cursor);

  /** Limit the number of returned entities. */
  CursorForwardPage limit(final Integer value);
}
