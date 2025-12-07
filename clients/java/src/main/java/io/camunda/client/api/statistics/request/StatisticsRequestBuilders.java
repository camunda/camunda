package io.camunda.client.api.statistics.request;

import io.camunda.client.api.statistics.sort.ProcessDefinitionInstanceStatisticsSort;
import io.camunda.client.impl.statistics.sort.ProcessDefinitionInstanceStatisticsSortImpl;
import java.util.function.Consumer;

public final class StatisticsRequestBuilders {

  public static ProcessDefinitionInstanceStatisticsSort processDefinitionInstanceStatisticsSort(
      final Consumer<ProcessDefinitionInstanceStatisticsSort> fn) {
    final ProcessDefinitionInstanceStatisticsSort sort =
        new ProcessDefinitionInstanceStatisticsSortImpl();
    fn.accept(sort);
    return sort;
  }
}
