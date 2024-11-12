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
import io.camunda.optimize.service.db.es.filter.DecisionQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.report.context.DateAggregationContext;
import java.util.Map;

public class DateAggregationContextES extends DateAggregationContext {

  private final Map<String, Aggregation.Builder.ContainerBuilder> subAggregations;
  private final DecisionQueryFilterEnhancerES decisionQueryFilterEnhancer;
  private final ProcessQueryFilterEnhancerES processQueryFilterEnhancer;

  protected DateAggregationContextES(final DateAggregationContextESBuilder<?, ?> b) {
    super(b);
    subAggregations = b.subAggregations;
    if (subAggregations == null) {
      throw new IllegalArgumentException("subAggregations cannot be null");
    }

    decisionQueryFilterEnhancer = b.decisionQueryFilterEnhancer;
    processQueryFilterEnhancer = b.processQueryFilterEnhancer;
  }

  public Map<String, ContainerBuilder> getSubAggregations() {
    return subAggregations;
  }

  public DecisionQueryFilterEnhancerES getDecisionQueryFilterEnhancer() {
    return decisionQueryFilterEnhancer;
  }

  public ProcessQueryFilterEnhancerES getProcessQueryFilterEnhancer() {
    return processQueryFilterEnhancer;
  }

  public static DateAggregationContextESBuilder<?, ?> builder() {
    return new DateAggregationContextESBuilderImpl();
  }

  public abstract static class DateAggregationContextESBuilder<
          C extends DateAggregationContextES, B extends DateAggregationContextESBuilder<C, B>>
      extends DateAggregationContextBuilder<C, B> {

    private Map<String, ContainerBuilder> subAggregations;
    private DecisionQueryFilterEnhancerES decisionQueryFilterEnhancer;
    private ProcessQueryFilterEnhancerES processQueryFilterEnhancer;

    public B subAggregations(final Map<String, ContainerBuilder> subAggregations) {
      if (subAggregations == null) {
        throw new IllegalArgumentException("subAggregations cannot be null");
      }

      this.subAggregations = subAggregations;
      return self();
    }

    public B decisionQueryFilterEnhancer(
        final DecisionQueryFilterEnhancerES decisionQueryFilterEnhancer) {
      this.decisionQueryFilterEnhancer = decisionQueryFilterEnhancer;
      return self();
    }

    public B processQueryFilterEnhancer(
        final ProcessQueryFilterEnhancerES processQueryFilterEnhancer) {
      this.processQueryFilterEnhancer = processQueryFilterEnhancer;
      return self();
    }

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "DateAggregationContextES.DateAggregationContextESBuilder(super="
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

  private static final class DateAggregationContextESBuilderImpl
      extends DateAggregationContextESBuilder<
          DateAggregationContextES, DateAggregationContextESBuilderImpl> {

    private DateAggregationContextESBuilderImpl() {}

    @Override
    protected DateAggregationContextESBuilderImpl self() {
      return this;
    }

    @Override
    public DateAggregationContextES build() {
      return new DateAggregationContextES(this);
    }
  }
}
