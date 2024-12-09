/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

public record UsageMetricsQuery(String startTime, String endTime) {
  public static UsageMetricsQuery of(final String startTime, final String endTime) {
    return new UsageMetricsQuery(startTime, endTime);
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<UsageMetricsQuery.Builder> {
    private String startTime;
    private String endTime;

    @Override
    protected UsageMetricsQuery.Builder self() {
      return this;
    }

    public UsageMetricsQuery.Builder startTime(final String value) {
      startTime = value;
      return this;
    }

    public UsageMetricsQuery.Builder endTime(final String value) {
      endTime = value;
      return this;
    }

    public UsageMetricsQuery build() {
      return new UsageMetricsQuery(startTime, endTime);
    }
  }
}
