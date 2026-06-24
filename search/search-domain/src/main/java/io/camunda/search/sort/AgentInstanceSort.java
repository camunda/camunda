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

public record AgentInstanceSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static AgentInstanceSort of(
      final Function<AgentInstanceSort.Builder, ObjectBuilder<AgentInstanceSort>> fn) {
    return SortOptionBuilders.agentInstance(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<AgentInstanceSort> {

    public Builder agentInstanceKey() {
      currentOrdering = new FieldSorting("agentInstanceKey", null);
      return this;
    }

    public Builder status() {
      currentOrdering = new FieldSorting("status", null);
      return this;
    }

    public Builder elementId() {
      currentOrdering = new FieldSorting("elementId", null);
      return this;
    }

    public Builder processInstanceKey() {
      currentOrdering = new FieldSorting("processInstanceKey", null);
      return this;
    }

    public Builder rootProcessInstanceKey() {
      currentOrdering = new FieldSorting("rootProcessInstanceKey", null);
      return this;
    }

    public Builder processDefinitionKey() {
      currentOrdering = new FieldSorting("processDefinitionKey", null);
      return this;
    }

    public Builder tenantId() {
      currentOrdering = new FieldSorting("tenantId", null);
      return this;
    }

    public Builder creationDate() {
      currentOrdering = new FieldSorting("creationDate", null);
      return this;
    }

    public Builder lastUpdatedDate() {
      currentOrdering = new FieldSorting("lastUpdatedDate", null);
      return this;
    }

    public Builder completionDate() {
      currentOrdering = new FieldSorting("completionDate", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public AgentInstanceSort build() {
      return new AgentInstanceSort(orderings);
    }
  }
}
