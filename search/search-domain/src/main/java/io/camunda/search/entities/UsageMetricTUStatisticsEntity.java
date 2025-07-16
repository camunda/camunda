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
    long totalAtu, Map<String, UsageMetricTUStatisticsEntityTenant> tenants) {

  public record UsageMetricTUStatisticsEntityTenant(long atu) {

    public static class Builder implements ObjectBuilder<UsageMetricTUStatisticsEntityTenant> {

      private long atu = 0;

      public UsageMetricTUStatisticsEntityTenant.Builder atu(final long atu) {
        this.atu = atu;
        return this;
      }

      @Override
      public UsageMetricTUStatisticsEntityTenant build() {
        return new UsageMetricTUStatisticsEntityTenant(atu);
      }
    }
  }
}
