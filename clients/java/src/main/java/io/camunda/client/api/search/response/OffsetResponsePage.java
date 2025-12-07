package io.camunda.client.api.search.response;

public interface OffsetResponsePage {

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
