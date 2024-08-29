/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregation;

import io.camunda.util.ObjectBuilder;
import java.util.List;

public record SearchTermsAggregate(List<Bucket> buckets) implements SearchAggregateOption {

  public static final class Builder implements ObjectBuilder<SearchTermsAggregate> {

    private List<Bucket> buckets;

    @Override
    public SearchTermsAggregate build() {
      return new SearchTermsAggregate(buckets);
    }

    public Builder buckets(final List<Bucket> buckets) {
      this.buckets = buckets;
      return this;
    }
  }
}
