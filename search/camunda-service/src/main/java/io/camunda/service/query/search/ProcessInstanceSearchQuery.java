package io.camunda.service.query.search;

import java.util.function.Function;
import io.camunda.service.query.filter.ProcessInstanceFilter;
import io.camunda.service.query.sort.ProcessInstanceSort;
import io.camunda.util.DataStoreObjectBuilder;

public final class ProcessInstanceSearchQuery extends SearchQueryBase<ProcessInstanceFilter, ProcessInstanceSort> {

  private ProcessInstanceSearchQuery(final Builder builder) {
    super(builder.filter, builder.sort, builder.page);
  }

  public static ProcessInstanceSearchQuery of(
      final Function<Builder, DataStoreObjectBuilder<ProcessInstanceSearchQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.Builder<ProcessInstanceFilter, ProcessInstanceSort, Builder> {

    public ProcessInstanceSearchQuery build() {
      if (filter == null) {
        throw new RuntimeException("no filter provided");
      }
      return new ProcessInstanceSearchQuery(this);
    }

    @Override
    protected Builder self() {
      return this;
    };
  }
}
