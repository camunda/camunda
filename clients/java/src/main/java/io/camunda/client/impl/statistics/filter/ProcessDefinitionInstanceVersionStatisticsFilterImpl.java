package io.camunda.client.impl.statistics.filter;

import io.camunda.client.api.statistics.filter.ProcessDefinitionInstanceVersionStatisticsFilter;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import java.util.function.Consumer;

public class ProcessDefinitionInstanceVersionStatisticsFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsFilter>
    implements ProcessDefinitionInstanceVersionStatisticsFilter {

  private final io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsFilter
      filter;

  public ProcessDefinitionInstanceVersionStatisticsFilterImpl() {
    filter = new io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsFilter();
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsFilter tenantId(final String value) {
    filter.setTenantId(value);
    return this;
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsFilter tenantId(final Consumer<String> fn) {
    final String value = provideSearchRequestProperty(fn);
    filter.setTenantId(value);
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsFilter
      getSearchRequestProperty() {
    return filter;
  }
}
