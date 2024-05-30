package io.camunda.service.query.sort;

import java.util.function.Function;
import io.camunda.util.DataStoreObjectBuilder;

public final class SortOptionBuilders {

  private SortOptionBuilders() {}

  public static ProcessInstanceSort.Builder processInstanceSort() {
    return new ProcessInstanceSort.Builder();
  }

  public static ProcessInstanceSort processInstance(
      final Function<ProcessInstanceSort.Builder, DataStoreObjectBuilder<ProcessInstanceSort>>
          fn) {
    return fn.apply(processInstanceSort()).build();
  }

}
