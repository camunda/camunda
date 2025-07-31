/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import io.camunda.util.ObjectBuilder;
import java.util.Map;

public record UsageMetricTUStatisticsEntity(
    long totalTu, Map<String, UsageMetricTUStatisticsEntityTenant> tenants) {

  public record UsageMetricTUStatisticsEntityTenant(long tu) {

    public static class Builder implements ObjectBuilder<UsageMetricTUStatisticsEntityTenant> {

      private long tu = 0;

      public UsageMetricTUStatisticsEntityTenant.Builder tu(final long tu) {
        this.tu = tu;
        return this;
      }

      @Override
      public UsageMetricTUStatisticsEntityTenant build() {
        return new UsageMetricTUStatisticsEntityTenant(tu);
      }
    }
  }
}
