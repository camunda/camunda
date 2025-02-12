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

public record AdHocSubprocessActivitySort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<AdHocSubprocessActivitySort> {

    public Builder flowNodeId() {
      currentOrdering = new FieldSorting("flowNodeId", null);
      return this;
    }

    public Builder flowNodeName() {
      currentOrdering = new FieldSorting("flowNodeName", null);
      return this;
    }

    public Builder type() {
      currentOrdering = new FieldSorting("type", null);
      return this;
    }

    public Builder treePath() {
      currentOrdering = new FieldSorting("treePath", null);
      return this;
    }

    @Override
    public AdHocSubprocessActivitySort build() {
      return new AdHocSubprocessActivitySort(orderings);
    }

    @Override
    protected Builder self() {
      return this;
    }
  }
}
