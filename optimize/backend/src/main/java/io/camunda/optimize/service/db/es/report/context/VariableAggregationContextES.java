/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.context;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation.Builder.ContainerBuilder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.camunda.optimize.service.db.report.context.VariableAggregationContext;
import java.util.Map;

public class VariableAggregationContextES extends VariableAggregationContext {

  private final BoolQuery baseQueryForMinMaxStats;
  private final Map<String, Aggregation.Builder.ContainerBuilder> subAggregations;

  protected VariableAggregationContextES(final VariableAggregationContextESBuilder<?, ?> b) {
    super(b);
    this.baseQueryForMinMaxStats = b.baseQueryForMinMaxStats;
    this.subAggregations = b.subAggregations;
  }

  public BoolQuery getBaseQueryForMinMaxStats() {
    return this.baseQueryForMinMaxStats;
  }

  public Map<String, ContainerBuilder> getSubAggregations() {
    return this.subAggregations;
  }

  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof VariableAggregationContextES;
  }

  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  public String toString() {
    return "VariableAggregationContextES(baseQueryForMinMaxStats="
        + this.getBaseQueryForMinMaxStats()
        + ", subAggregations="
        + this.getSubAggregations()
        + ")";
  }

  public static VariableAggregationContextESBuilder<?, ?> builder() {
    return new VariableAggregationContextESBuilderImpl();
  }

  public abstract static class VariableAggregationContextESBuilder<
          C extends VariableAggregationContextES,
          B extends VariableAggregationContextESBuilder<C, B>>
      extends VariableAggregationContextBuilder<C, B> {

    private BoolQuery baseQueryForMinMaxStats;
    private Map<String, ContainerBuilder> subAggregations;

    public B baseQueryForMinMaxStats(final BoolQuery baseQueryForMinMaxStats) {
      this.baseQueryForMinMaxStats = baseQueryForMinMaxStats;
      return self();
    }

    public B subAggregations(final Map<String, ContainerBuilder> subAggregations) {
      this.subAggregations = subAggregations;
      return self();
    }

    protected abstract B self();

    public abstract C build();

    public String toString() {
      return "VariableAggregationContextES.VariableAggregationContextESBuilder(super="
          + super.toString()
          + ", baseQueryForMinMaxStats="
          + this.baseQueryForMinMaxStats
          + ", subAggregations="
          + this.subAggregations
          + ")";
    }
  }

  private static final class VariableAggregationContextESBuilderImpl
      extends VariableAggregationContextESBuilder<
          VariableAggregationContextES, VariableAggregationContextESBuilderImpl> {

    private VariableAggregationContextESBuilderImpl() {}

    protected VariableAggregationContextESBuilderImpl self() {
      return this;
    }

    public VariableAggregationContextES build() {
      return new VariableAggregationContextES(this);
    }
  }
}
