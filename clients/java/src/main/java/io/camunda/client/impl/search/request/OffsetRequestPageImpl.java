package io.camunda.client.impl.search.request;

import io.camunda.client.api.search.request.OffsetRequestPage;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceStatisticsPageRequest;

public class OffsetRequestPageImpl
    extends TypedSearchRequestPropertyProvider<ProcessDefinitionInstanceStatisticsPageRequest>
    implements OffsetRequestPage {

  private final ProcessDefinitionInstanceStatisticsPageRequest page =
      new ProcessDefinitionInstanceStatisticsPageRequest();

  @Override
  public OffsetRequestPage from(final Integer value) {
    page.setFrom(value);
    return this;
  }

  @Override
  public OffsetRequestPage limit(final Integer value) {
    page.setLimit(value);
    return this;
  }

  @Override
  protected ProcessDefinitionInstanceStatisticsPageRequest getSearchRequestProperty() {
    return page;
  }
}
