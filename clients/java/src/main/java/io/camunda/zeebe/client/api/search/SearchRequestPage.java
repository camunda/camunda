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

import java.util.List;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.search.request.SearchRequestPage}
 */
@Deprecated
public interface SearchRequestPage {

  /** Start the page from. */
  SearchRequestPage from(final Integer value);

  /** Limit the the number of returned entities. */
  SearchRequestPage limit(final Integer value);

  /** Get previous page before the set of values. */
  SearchRequestPage searchBefore(final List<Object> values);

  /** Get next page after the set of values. */
  SearchRequestPage searchAfter(final List<Object> values);
}
