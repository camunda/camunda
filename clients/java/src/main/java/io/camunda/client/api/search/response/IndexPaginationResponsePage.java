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

public interface IndexPaginationResponsePage {

  /** Total number of items that matches the query */
  Long totalItems();

  /**
   * Whether the total items count exceeds the maximum limit in Elasticsearch (ES) or OpenSearch
   * (OS).
   *
   * <p>In ES or OS, total items are often capped by a predefined configurable limit. If the result
   * set is greater than or equal to this, this method returns {@code true}; otherwise, it returns
   * {@code false}.
   *
   * <p>For RDBMS-backed searches, this is always {@code false} because there is no such limitation.
   *
   * <p>This helps clients understand when total item counts may be incomplete due to ES or OS
   * limits.
   *
   * @return {@code true} if the total result count exceeds the cap in ES or OS; {@code false}
   *     otherwise.
   */
  default Boolean hasMoreTotalItems() {
    return false;
  }
}
