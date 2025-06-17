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

public record UsageMetricStatisticsEntity(
    long rpi, long edi, long at, Map<String, UsageMetricStatisticsEntityTenant> tenants) {

  public record UsageMetricStatisticsEntityTenant(long rpi, long edi) {

    public static class Builder implements ObjectBuilder<UsageMetricStatisticsEntityTenant> {

      private long rpi = 0;
      private long edi = 0;

      public Builder rpi(final long rpi) {
        this.rpi = rpi;
        return this;
      }

      public Builder edi(final long edi) {
        this.edi = edi;
        return this;
      }

      @Override
      public UsageMetricStatisticsEntityTenant build() {
        return new UsageMetricStatisticsEntityTenant(rpi, edi);
      }
    }
  }
}
