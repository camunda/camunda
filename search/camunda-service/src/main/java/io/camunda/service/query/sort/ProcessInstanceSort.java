package io.camunda.service.query.sort;

import io.camunda.data.clients.sort.DataStoreSortOptions;
import io.camunda.data.clients.sort.SortOrder;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final record ProcessInstanceSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<DataStoreSortOptions> toSortOptions(boolean reverse) {
    if (orderings != null && !orderings.isEmpty()) {
      return orderings.stream().map(s -> s.toSortOption(reverse)).toList();
    }
    return null;
  }

  public static ProcessInstanceSort of(final Function<Builder, DataStoreObjectBuilder<ProcessInstanceSort>> fn) {
    return SortOptionBuilders.processInstance(fn);
  }

  public static final class Builder implements DataStoreObjectBuilder<ProcessInstanceSort> {

    private List<FieldSorting> orderings = new ArrayList<>();
    private FieldSorting currentOrdering;

    public Builder processInstanceKey() {
      currentOrdering = new FieldSorting("processInstanceKey", null);
      return this;
    }

    public Builder startDate() {
      currentOrdering = new FieldSorting("startDate", null);
      return this;
    }

    public Builder endDate() {
      currentOrdering = new FieldSorting("endDate", null);
      return this;
    }

    private Builder order(final SortOrder order) {
      if (currentOrdering == null) {
        final var field = currentOrdering.field();
        final var newOrdering = new FieldSorting(field, SortOrder.ASC);
        orderings.add(newOrdering);
        currentOrdering = null;
      }
      // else if not set, then it's noop

      return this;
    }
    
    public Builder asc() {
      return order(SortOrder.ASC);
    }
    
    public Builder desc() {
      return order(SortOrder.DESC);
    }

    @Override
    public ProcessInstanceSort build() {
      // TODO exception handling
      if (currentOrdering != null) {
        throw new RuntimeException("sorting not done");
      }
      return new ProcessInstanceSort(orderings);
    }
  }
}
