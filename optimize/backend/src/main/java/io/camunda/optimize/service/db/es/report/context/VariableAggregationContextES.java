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

  protected VariableAggregationContextES(VariableAggregationContextESBuilder<?, ?> b) {
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
    if (o == this) {
      return true;
    }
    if (!(o instanceof VariableAggregationContextES)) {
      return false;
    }
    final VariableAggregationContextES other = (VariableAggregationContextES) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$baseQueryForMinMaxStats = this.getBaseQueryForMinMaxStats();
    final Object other$baseQueryForMinMaxStats = other.getBaseQueryForMinMaxStats();
    if (this$baseQueryForMinMaxStats == null
        ? other$baseQueryForMinMaxStats != null
        : !this$baseQueryForMinMaxStats.equals(other$baseQueryForMinMaxStats)) {
      return false;
    }
    final Object this$subAggregations = this.getSubAggregations();
    final Object other$subAggregations = other.getSubAggregations();
    if (this$subAggregations == null
        ? other$subAggregations != null
        : !this$subAggregations.equals(other$subAggregations)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof VariableAggregationContextES;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $baseQueryForMinMaxStats = this.getBaseQueryForMinMaxStats();
    result =
        result * PRIME
            + ($baseQueryForMinMaxStats == null ? 43 : $baseQueryForMinMaxStats.hashCode());
    final Object $subAggregations = this.getSubAggregations();
    result = result * PRIME + ($subAggregations == null ? 43 : $subAggregations.hashCode());
    return result;
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

    public B baseQueryForMinMaxStats(BoolQuery baseQueryForMinMaxStats) {
      this.baseQueryForMinMaxStats = baseQueryForMinMaxStats;
      return self();
    }

    public B subAggregations(Map<String, ContainerBuilder> subAggregations) {
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
