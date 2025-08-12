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

public record MessageSubscriptionSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static MessageSubscriptionSort of(
      final Function<MessageSubscriptionSort.Builder, ObjectBuilder<MessageSubscriptionSort>> fn) {
    return SortOptionBuilders.messageSubscription(fn);
  }

  public static final class Builder
      extends SortOption.AbstractBuilder<MessageSubscriptionSort.Builder>
      implements ObjectBuilder<MessageSubscriptionSort> {

    public Builder messageSubscriptionKey() {
      currentOrdering = new FieldSorting("messageSubscriptionKey", null);
      return this;
    }

    public Builder processDefinitionId() {
      currentOrdering = new FieldSorting("processDefinitionId", null);
      return this;
    }

    public Builder processInstanceKey() {
      currentOrdering = new FieldSorting("processInstanceKey", null);
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

    public Builder messageSubscriptionType() {
      currentOrdering = new FieldSorting("messageSubscriptionType", null);
      return this;
    }

    public Builder dateTime() {
      currentOrdering = new FieldSorting("dateTime", null);
      return this;
    }

    public Builder messageName() {
      currentOrdering = new FieldSorting("messageName", null);
      return this;
    }

    public Builder correlationKey() {
      currentOrdering = new FieldSorting("correlationKey", null);
      return this;
    }

    public Builder tenantId() {
      currentOrdering = new FieldSorting("tenantId", null);
      return this;
    }

    @Override
    protected MessageSubscriptionSort.Builder self() {
      return this;
    }

    @Override
    public MessageSubscriptionSort build() {
      return new MessageSubscriptionSort(orderings);
    }
  }
}
