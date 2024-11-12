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

public record DecisionInstanceSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static DecisionInstanceSort of(
      final Function<Builder, ObjectBuilder<DecisionInstanceSort>> fn) {
    return SortOptionBuilders.decisionInstance(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<DecisionInstanceSort> {

    public Builder decisionInstanceKey() {
      currentOrdering = new FieldSorting("key", null);
      return this;
    }

    public Builder state() {
      currentOrdering = new FieldSorting("state", null);
      return this;
    }

    public Builder evaluationDate() {
      currentOrdering = new FieldSorting("evaluationDate", null);
      return this;
    }

    public Builder evaluationFailure() {
      currentOrdering = new FieldSorting("evaluationFailure", null);
      return this;
    }

    public Builder processDefinitionKey() {
      currentOrdering = new FieldSorting("processDefinitionKey", null);
      return this;
    }

    public Builder processInstanceId() {
      currentOrdering = new FieldSorting("processInstanceId", null);
      return this;
    }

    public Builder decisionDefinitionKey() {
      currentOrdering = new FieldSorting("decisionId", null);
      return this;
    }

    public Builder decisionDefinitionId() {
      currentOrdering = new FieldSorting("decisionDefinitionId", null);
      return this;
    }

    public Builder decisionDefinitionName() {
      currentOrdering = new FieldSorting("decisionName", null);
      return this;
    }

    public Builder decisionDefinitionVersion() {
      currentOrdering = new FieldSorting("decisionVersion", null);
      return this;
    }

    public Builder decisionDefinitionType() {
      currentOrdering = new FieldSorting("decisionType", null);
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
    public DecisionInstanceSort build() {
      return new DecisionInstanceSort(orderings);
    }
  }
}
