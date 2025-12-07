package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.response.OffsetResponse;
import io.camunda.client.api.search.response.OffsetResponsePage;
import java.util.List;

public class OffsetResponseImpl<T> implements OffsetResponse<T> {

  private final List<T> items;
  private final OffsetResponsePage page;

  public OffsetResponseImpl(final List<T> items, final OffsetResponsePage page) {
    this.items = items;
    this.page = page;
  }

  @Override
  public OffsetResponsePage page() {
    return page;
  }

  @Override
  public List<T> items() {
    return items;
  }
}
