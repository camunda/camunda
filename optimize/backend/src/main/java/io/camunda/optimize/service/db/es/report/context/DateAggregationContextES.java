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

  protected DateAggregationContextES(DateAggregationContextESBuilder<?, ?> b) {
    super(b);
    this.subAggregations = b.subAggregations;
    if (subAggregations == null) {
      throw new IllegalArgumentException("subAggregations cannot be null");
    }

    this.decisionQueryFilterEnhancer = b.decisionQueryFilterEnhancer;
    this.processQueryFilterEnhancer = b.processQueryFilterEnhancer;
  }

  public Map<String, ContainerBuilder> getSubAggregations() {
    return this.subAggregations;
  }

  public DecisionQueryFilterEnhancerES getDecisionQueryFilterEnhancer() {
    return this.decisionQueryFilterEnhancer;
  }

  public ProcessQueryFilterEnhancerES getProcessQueryFilterEnhancer() {
    return this.processQueryFilterEnhancer;
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

    public B subAggregations(Map<String, ContainerBuilder> subAggregations) {
      if (subAggregations == null) {
        throw new IllegalArgumentException("subAggregations cannot be null");
      }

      this.subAggregations = subAggregations;
      return self();
    }

    public B decisionQueryFilterEnhancer(
        DecisionQueryFilterEnhancerES decisionQueryFilterEnhancer) {
      this.decisionQueryFilterEnhancer = decisionQueryFilterEnhancer;
      return self();
    }

    public B processQueryFilterEnhancer(ProcessQueryFilterEnhancerES processQueryFilterEnhancer) {
      this.processQueryFilterEnhancer = processQueryFilterEnhancer;
      return self();
    }

    protected abstract B self();

    public abstract C build();

    public String toString() {
      return "DateAggregationContextES.DateAggregationContextESBuilder(super="
          + super.toString()
          + ", subAggregations="
          + this.subAggregations
          + ", decisionQueryFilterEnhancer="
          + this.decisionQueryFilterEnhancer
          + ", processQueryFilterEnhancer="
          + this.processQueryFilterEnhancer
          + ")";
    }
  }

  private static final class DateAggregationContextESBuilderImpl
      extends DateAggregationContextESBuilder<
          DateAggregationContextES, DateAggregationContextESBuilderImpl> {

    private DateAggregationContextESBuilderImpl() {}

    protected DateAggregationContextESBuilderImpl self() {
      return this;
    }

    public DateAggregationContextES build() {
      return new DateAggregationContextES(this);
    }
  }
}
