package io.camunda.client.api.search.response;

public interface OffsetResponse<T> extends BaseResponse<T> {

  /** Returns information about the returned page of items */
  OffsetResponsePage page();
}
