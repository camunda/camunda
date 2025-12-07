package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.response.OffsetResponsePage;

public class OffsetResponsePageImpl implements OffsetResponsePage {

  private final Long totalItems;
  private final Boolean hasMoreTotalItems;

  public OffsetResponsePageImpl(final Long totalItems, final Boolean hasMoreTotalItems) {
    this.totalItems = totalItems;
    this.hasMoreTotalItems = hasMoreTotalItems;
  }

  @Override
  public Long totalItems() {
    return totalItems;
  }

  @Override
  public Boolean hasMoreTotalItems() {
    return hasMoreTotalItems;
  }
}
