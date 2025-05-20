package io.camunda.search.sort;

import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.function.Function;

public record JobSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static JobSort of(final Function<Builder, ObjectBuilder<JobSort>> fn) {
    return SortOptionBuilders.job(fn);
  }

  public static final class Builder extends SortOption.AbstractBuilder<JobSort.Builder>
      implements ObjectBuilder<JobSort> {

    public JobSort.Builder processDefinitionKey() {
      currentOrdering = new FieldSorting("processDefinitionKey", null);
      return this;
    }

    public JobSort.Builder processInstanceKey() {
      currentOrdering = new FieldSorting("processInstanceKey", null);
      return this;
    }

    public JobSort.Builder elementInstanceKey() {
      currentOrdering = new FieldSorting("elementInstanceKey", null);
      return this;
    }

    public JobSort.Builder elementId() {
      currentOrdering = new FieldSorting("elementId", null);
      return this;
    }

    public JobSort.Builder jobKey() {
      currentOrdering = new FieldSorting("jobKey", null);
      return this;
    }

    public JobSort.Builder type() {
      currentOrdering = new FieldSorting("type", null);
      return this;
    }

    public JobSort.Builder worker() {
      currentOrdering = new FieldSorting("worker", null);
      return this;
    }

    public JobSort.Builder state() {
      currentOrdering = new FieldSorting("state", null);
      return this;
    }

    public JobSort.Builder jobKind() {
      currentOrdering = new FieldSorting("jobKind", null);
      return this;
    }

    public JobSort.Builder listenerEventType() {
      currentOrdering = new FieldSorting("listenerEventType", null);
      return this;
    }

    public JobSort.Builder endDate() {
      currentOrdering = new FieldSorting("endDate", null);
      return this;
    }

    public JobSort.Builder tenantId() {
      currentOrdering = new FieldSorting("tenantId", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public JobSort build() {
      return new JobSort(orderings);
    }
  }
}
