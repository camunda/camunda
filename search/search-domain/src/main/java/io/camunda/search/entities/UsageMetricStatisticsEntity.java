/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import java.util.Map;

public record UsageMetricStatisticsEntity(
    long rpi, long edi, long at, Map<String, UsageMetricStatisticsEntityTenant> tenants) {

  public record UsageMetricStatisticsEntityTenant(long rpi, long edi) {

    public static UsageMetricStatisticsEntityTenant merge(
        final UsageMetricStatisticsEntityTenant a, final UsageMetricStatisticsEntityTenant b) {
      return new UsageMetricStatisticsEntityTenant(a.rpi() + b.rpi(), a.edi() + b.edi());
    }
  }
}
