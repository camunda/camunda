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

public record IncidentSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static IncidentSort of(final Function<Builder, ObjectBuilder<IncidentSort>> fn) {
    return SortOptionBuilders.incident(fn);
  }

  public static final class Builder extends SortOption.AbstractBuilder<IncidentSort.Builder>
      implements ObjectBuilder<IncidentSort> {

    public Builder incidentKey() {
      currentOrdering = new FieldSorting("key", null);
      return this;
    }

    public Builder processDefinitionKey() {
      currentOrdering = new FieldSorting("processDefinitionKey", null);
      return this;
    }

    public Builder processDefinitionId() {
      currentOrdering = new FieldSorting("bpmnProcessId", null);
      return this;
    }

    public Builder processInstanceKey() {
      currentOrdering = new FieldSorting("processInstanceKey", null);
      return this;
    }

    public Builder errorType() {
      currentOrdering = new FieldSorting("errorType", null);
      return this;
    }

    public Builder errorMessage() {
      currentOrdering = new FieldSorting("errorMessage", null);
      return this;
    }

    public Builder flowNodeId() {
      currentOrdering = new FieldSorting("flowNodeId", null);
      return this;
    }

    public Builder flowNodeInstanceKey() {
      currentOrdering = new FieldSorting("flowNodeInstanceKey", null);
      return this;
    }

    public Builder creationTime() {
      currentOrdering = new FieldSorting("creationTime", null);
      return this;
    }

    public Builder state() {
      currentOrdering = new FieldSorting("state", null);
      return this;
    }

    public Builder jobKey() {
      currentOrdering = new FieldSorting("jobKey", null);
      return this;
    }

    public Builder treePath() {
      currentOrdering = new FieldSorting("treePath", null);
      return this;
    }

    public Builder tenantId() {
      currentOrdering = new FieldSorting("tenantId", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public IncidentSort build() {
      return new IncidentSort(orderings);
    }
  }
}
