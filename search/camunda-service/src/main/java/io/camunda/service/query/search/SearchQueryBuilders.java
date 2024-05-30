package io.camunda.service.query.search;

import io.camunda.service.query.search.ProcessInstanceSearchQuery.Builder;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public final class SearchQueryBuilders {

  public static ProcessInstanceSearchQuery.Builder processInstanceSearchQuery() {
    return new ProcessInstanceSearchQuery.Builder();
  }

  public static ProcessInstanceSearchQuery processInstanceSearchQuery(
      final Function<Builder, DataStoreObjectBuilder<ProcessInstanceSearchQuery>> fn) {
    return fn.apply(processInstanceSearchQuery()).build();
  }

}
