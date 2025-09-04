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

public record CorrelatedMessagesSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static CorrelatedMessagesSort of(
      final Function<CorrelatedMessagesSort.Builder, ObjectBuilder<CorrelatedMessagesSort>> fn) {
    return SortOptionBuilders.correlatedMessages(fn);
  }

  public static final class Builder
      extends SortOption.AbstractBuilder<CorrelatedMessagesSort.Builder>
      implements ObjectBuilder<CorrelatedMessagesSort> {

    public Builder correlationKey() {
      currentOrdering = new FieldSorting("correlationKey", null);
      return this;
    }

    public Builder correlationTime() {
      currentOrdering = new FieldSorting("correlationTime", null);
      return this;
    }

    public Builder elementId() {
      currentOrdering = new FieldSorting("elementId", null);
      return this;
    }

    public Builder elementInstanceKey() {
      currentOrdering = new FieldSorting("elementInstanceKey", null);
      return this;
    }

    public Builder messageKey() {
      currentOrdering = new FieldSorting("messageKey", null);
      return this;
    }

    public Builder messageName() {
      currentOrdering = new FieldSorting("messageName", null);
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

    public Builder processInstanceKey() {
      currentOrdering = new FieldSorting("processInstanceKey", null);
      return this;
    }

    public Builder subscriptionKey() {
      currentOrdering = new FieldSorting("subscriptionKey", null);
      return this;
    }

    public Builder tenantId() {
      currentOrdering = new FieldSorting("tenantId", null);
      return this;
    }

    @Override
    protected CorrelatedMessagesSort.Builder self() {
      return this;
    }

    @Override
    public CorrelatedMessagesSort build() {
      return new CorrelatedMessagesSort(orderings);
    }
  }
}