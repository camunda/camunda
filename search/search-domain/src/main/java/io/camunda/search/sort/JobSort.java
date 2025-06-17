/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
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
      currentOrdering = new FieldSorting("kind", null);
      return this;
    }

    public JobSort.Builder listenerEventType() {
      currentOrdering = new FieldSorting("listenerEventType", null);
      return this;
    }

    public JobSort.Builder endTime() {
      currentOrdering = new FieldSorting("endTime", null);
      return this;
    }

    public JobSort.Builder tenantId() {
      currentOrdering = new FieldSorting("tenantId", null);
      return this;
    }

    public JobSort.Builder retries() {
      currentOrdering = new FieldSorting("retries", null);
      return this;
    }

    public JobSort.Builder isDenied() {
      currentOrdering = new FieldSorting("isDenied", null);
      return this;
    }

    public JobSort.Builder deniedReason() {
      currentOrdering = new FieldSorting("deniedReason", null);
      return this;
    }

    public JobSort.Builder hasFailedWithRetriesLeft() {
      currentOrdering = new FieldSorting("hasFailedWithRetriesLeft", null);
      return this;
    }

    public JobSort.Builder errorCode() {
      currentOrdering = new FieldSorting("errorCode", null);
      return this;
    }

    public JobSort.Builder errorMessage() {
      currentOrdering = new FieldSorting("errorMessage", null);
      return this;
    }

    public JobSort.Builder deadline() {
      currentOrdering = new FieldSorting("deadline", null);
      return this;
    }

    public JobSort.Builder processDefinitionId() {
      currentOrdering = new FieldSorting("processDefinitionId", null);
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
