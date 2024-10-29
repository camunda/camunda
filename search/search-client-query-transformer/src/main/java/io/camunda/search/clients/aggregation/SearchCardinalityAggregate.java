/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregation;

import io.camunda.util.ObjectBuilder;
import java.util.Objects;

public record SearchCardinalityAggregate(Long value) implements SearchAggregateOption {

  public static final class Builder implements ObjectBuilder<SearchCardinalityAggregate> {

    private Long value;

    public Builder value(final Long value) {
      this.value = value;
      return this;
    }

    @Override
    public SearchCardinalityAggregate build() {
      return new SearchCardinalityAggregate(
          Objects.requireNonNull(value, "Expected non-null field for cardinality aggregate value"));
    }
  }
}
