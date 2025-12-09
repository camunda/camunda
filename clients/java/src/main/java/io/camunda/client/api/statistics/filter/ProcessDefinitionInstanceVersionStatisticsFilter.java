package io.camunda.client.api.statistics.filter;

import io.camunda.client.api.statistics.request.StatisticsRequest.StatisticsRequestFilter;
import java.util.function.Consumer;

public interface ProcessDefinitionInstanceVersionStatisticsFilter extends StatisticsRequestFilter {

  /**
   * Filters process definition by tenant id.
   *
   * @param value the id of tenant
   * @return the updated filter
   */
  ProcessDefinitionInstanceVersionStatisticsFilter tenantId(final String value);

  /**
   * Filters process definition by tenant id using consumer.
   *
   * @param fn consumer to create the string property
   * @return the updated filter
   */
  ProcessDefinitionInstanceVersionStatisticsFilter tenantId(final Consumer<String> fn);
}
