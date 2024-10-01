/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.context;

import io.camunda.optimize.service.db.report.context.VariableAggregationContext;
import java.util.Map;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class VariableAggregationContextOS extends VariableAggregationContext {

  private final Query baseQueryForMinMaxStats;
  private final Map<String, Aggregation> subAggregations;

  protected VariableAggregationContextOS(VariableAggregationContextOSBuilder<?, ?> b) {
    super(b);
    this.baseQueryForMinMaxStats = b.baseQueryForMinMaxStats;
    this.subAggregations = b.subAggregations;
  }

  public Query getBaseQueryForMinMaxStats() {
    return this.baseQueryForMinMaxStats;
  }

  public Map<String, Aggregation> getSubAggregations() {
    return this.subAggregations;
  }

  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof VariableAggregationContextOS)) {
      return false;
    }
    final VariableAggregationContextOS other = (VariableAggregationContextOS) o;
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
    return other instanceof VariableAggregationContextOS;
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
    return "VariableAggregationContextOS(baseQueryForMinMaxStats="
        + this.getBaseQueryForMinMaxStats()
        + ", subAggregations="
        + this.getSubAggregations()
        + ")";
  }

  public static VariableAggregationContextOSBuilder<?, ?> builder() {
    return new VariableAggregationContextOSBuilderImpl();
  }

  public abstract static class VariableAggregationContextOSBuilder<
          C extends VariableAggregationContextOS,
          B extends VariableAggregationContextOSBuilder<C, B>>
      extends VariableAggregationContextBuilder<C, B> {

    private Query baseQueryForMinMaxStats;
    private Map<String, Aggregation> subAggregations;

    public B baseQueryForMinMaxStats(Query baseQueryForMinMaxStats) {
      this.baseQueryForMinMaxStats = baseQueryForMinMaxStats;
      return self();
    }

    public B subAggregations(Map<String, Aggregation> subAggregations) {
      this.subAggregations = subAggregations;
      return self();
    }

    protected abstract B self();

    public abstract C build();

    public String toString() {
      return "VariableAggregationContextOS.VariableAggregationContextOSBuilder(super="
          + super.toString()
          + ", baseQueryForMinMaxStats="
          + this.baseQueryForMinMaxStats
          + ", subAggregations="
          + this.subAggregations
          + ")";
    }
  }

  private static final class VariableAggregationContextOSBuilderImpl
      extends VariableAggregationContextOSBuilder<
          VariableAggregationContextOS, VariableAggregationContextOSBuilderImpl> {

    private VariableAggregationContextOSBuilderImpl() {}

    protected VariableAggregationContextOSBuilderImpl self() {
      return this;
    }

    public VariableAggregationContextOS build() {
      return new VariableAggregationContextOS(this);
    }
  }
}
