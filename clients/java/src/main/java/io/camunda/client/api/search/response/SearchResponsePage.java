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
package io.camunda.client.api.search.response;

public interface SearchResponsePage {

  /** Total number of items that matches the query */
  Long totalItems();

  /**
   * Indicates whether there are more results available beyond the default maximum threshold
   * of 10_000 items in search responses from Elasticsearch or OpenSearch (ES/OS).
   * <p>
   * In ES/OS-backed searches, the {@code totalItems} value is capped at 10_000 for performance reasons.
   * If the underlying ES/OS response returns {@code hits.total.relation = "gte"}, meaning the actual
   * number of results is greater than or equal to 10_000, this method returns {@code true}.
   * Otherwise, it returns {@code false}.
   * <p>
   * This helps API consumers detect when the reported {@code totalItems} count is not the full count,
   * and more items may be available than are indicated.
   *
   * @return {@code true} if more results exist than the default ES/OS maximum of 10_000 items; {@code false} otherwise.
   */
  Boolean hasMoreTotalItems();

  /** The cursor to the first item in the returned page. */
  String startCursor();

  /** The cursor to the last item in the returned page. */
  String endCursor();
}
