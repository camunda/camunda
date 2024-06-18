package io.camunda.zeebe.client.api.search;

import java.util.function.Consumer;
import io.camunda.zeebe.client.impl.search.ProcessInstanceFilterImpl;
import io.camunda.zeebe.client.impl.search.ProcessInstanceSortImpl;

public final class SearchBuilders {

  private SearchBuilders() {}

  public static ProcessInstanceFilter processInstanceFilter() {
    return new ProcessInstanceFilterImpl();
  }

  public static ProcessInstanceFilter processInstanceFilter(final Consumer<ProcessInstanceFilter> fn) {
    final ProcessInstanceFilter filter = processInstanceFilter();
    fn.accept(filter);
    return filter;
  }

  public static ProcessInstanceSort processInstanceSort() {
    return new ProcessInstanceSortImpl();
  }

  public static ProcessInstanceSort processInstanceSort(final Consumer<ProcessInstanceSort> fn) {
    final ProcessInstanceSort sort = processInstanceSort();
    fn.accept(sort);
    return sort;
  }

}
