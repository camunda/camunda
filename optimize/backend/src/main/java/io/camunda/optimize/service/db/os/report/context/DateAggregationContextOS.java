/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.context;

import io.camunda.optimize.service.db.os.report.filter.DecisionQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.os.report.filter.ProcessQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.report.context.DateAggregationContext;
import java.util.Map;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;

public class DateAggregationContextOS extends DateAggregationContext {

  private final Map<String, Aggregation> subAggregations;
  private final DecisionQueryFilterEnhancerOS decisionQueryFilterEnhancer;
  private final ProcessQueryFilterEnhancerOS processQueryFilterEnhancer;

  protected DateAggregationContextOS(final DateAggregationContextOSBuilder<?, ?> b) {
    super(b);
    subAggregations = b.subAggregations;
    if (subAggregations == null) {
      throw new IllegalArgumentException("subAggregations cannot be null");
    }

    decisionQueryFilterEnhancer = b.decisionQueryFilterEnhancer;
    processQueryFilterEnhancer = b.processQueryFilterEnhancer;
  }

  public Map<String, Aggregation> getSubAggregations() {
    return subAggregations;
  }

  public DecisionQueryFilterEnhancerOS getDecisionQueryFilterEnhancer() {
    return decisionQueryFilterEnhancer;
  }

  public ProcessQueryFilterEnhancerOS getProcessQueryFilterEnhancer() {
    return processQueryFilterEnhancer;
  }

  public static DateAggregationContextOSBuilder<?, ?> builder() {
    return new DateAggregationContextOSBuilderImpl();
  }

  public abstract static class DateAggregationContextOSBuilder<
          C extends DateAggregationContextOS, B extends DateAggregationContextOSBuilder<C, B>>
      extends DateAggregationContextBuilder<C, B> {

    private Map<String, Aggregation> subAggregations;
    private DecisionQueryFilterEnhancerOS decisionQueryFilterEnhancer;
    private ProcessQueryFilterEnhancerOS processQueryFilterEnhancer;

    public B subAggregations(final Map<String, Aggregation> subAggregations) {
      if (subAggregations == null) {
        throw new IllegalArgumentException("subAggregations cannot be null");
      }

      this.subAggregations = subAggregations;
      return self();
    }

    public B decisionQueryFilterEnhancer(
        final DecisionQueryFilterEnhancerOS decisionQueryFilterEnhancer) {
      this.decisionQueryFilterEnhancer = decisionQueryFilterEnhancer;
      return self();
    }

    public B processQueryFilterEnhancer(
        final ProcessQueryFilterEnhancerOS processQueryFilterEnhancer) {
      this.processQueryFilterEnhancer = processQueryFilterEnhancer;
      return self();
    }

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "DateAggregationContextOS.DateAggregationContextOSBuilder(super="
          + super.toString()
          + ", subAggregations="
          + subAggregations
          + ", decisionQueryFilterEnhancer="
          + decisionQueryFilterEnhancer
          + ", processQueryFilterEnhancer="
          + processQueryFilterEnhancer
          + ")";
    }
  }

  private static final class DateAggregationContextOSBuilderImpl
      extends DateAggregationContextOSBuilder<
          DateAggregationContextOS, DateAggregationContextOSBuilderImpl> {

    private DateAggregationContextOSBuilderImpl() {}

    @Override
    protected DateAggregationContextOSBuilderImpl self() {
      return this;
    }

    @Override
    public DateAggregationContextOS build() {
      return new DateAggregationContextOS(this);
    }
  }
}
