package io.camunda.client.api.search.request;

public interface OffsetRequestPage {

  /** Start the page from. */
  OffsetRequestPage from(final Integer value);

  /** Limit the number of returned entities. */
  OffsetRequestPage limit(final Integer value);
}
