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

/**
 * @deprecated Replaced by {@link io.camunda.client.api.search.page.AnyPage} and the other typed
 *     pagination models in the {@link io.camunda.client.api.search.page} package. This interface is
 *     <b>not</b> source-compatible with the new pagination types — it cannot be passed to {@code
 *     .page(...)} on typed search requests because it does not extend {@link
 *     io.camunda.client.api.search.page.SearchPagination}. Use {@link
 *     io.camunda.client.api.search.request.SearchRequestBuilders#anyPage(java.util.function.Consumer)}
 *     (or {@code offsetPage}, {@code cursorForwardPage}, etc.) as a direct replacement. This
 *     interface will be removed in a future release.
 */
@Deprecated
public interface SearchRequestPage {

  /** Start the page from. */
  SearchRequestPage from(final Integer value);

  /** Limit the number of returned entities. */
  SearchRequestPage limit(final Integer value);

  /** Get previous page before the cursor. */
  SearchRequestPage before(final String cursor);

  /** Get next page after the cursor. */
  SearchRequestPage after(final String cursor);
}
