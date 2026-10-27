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

public record AgentDefinitionSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static AgentDefinitionSort of(
      final Function<Builder, ObjectBuilder<AgentDefinitionSort>> fn) {
    return SortOptionBuilders.agentDefinition(fn);
  }

  public static final class Builder extends SortOption.AbstractBuilder<Builder>
      implements ObjectBuilder<AgentDefinitionSort> {

    public Builder agentDefinitionKey() {
      currentOrdering = new FieldSorting("agentDefinitionKey", null);
      return this;
    }

    public Builder agentType() {
      currentOrdering = new FieldSorting("agentType", null);
      return this;
    }

    public Builder name() {
      currentOrdering = new FieldSorting("name", null);
      return this;
    }

    public Builder elementId() {
      currentOrdering = new FieldSorting("elementId", null);
      return this;
    }

    public Builder processDefinitionId() {
      currentOrdering = new FieldSorting("processDefinitionId", null);
      return this;
    }

    public Builder processDefinitionKey() {
      currentOrdering = new FieldSorting("processDefinitionKey", null);
      return this;
    }

    public Builder processDefinitionVersion() {
      currentOrdering = new FieldSorting("processDefinitionVersion", null);
      return this;
    }

    public Builder processDefinitionVersionTag() {
      currentOrdering = new FieldSorting("processDefinitionVersionTag", null);
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
    public AgentDefinitionSort build() {
      return new AgentDefinitionSort(orderings);
    }
  }
}
