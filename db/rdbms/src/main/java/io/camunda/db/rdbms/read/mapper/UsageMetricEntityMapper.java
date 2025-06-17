/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.UsageMetricDbModel.UsageMetricStatisticsDbModel;
import io.camunda.search.entities.UsageMetricStatisticsEntity;
import io.camunda.search.entities.UsageMetricStatisticsEntity.UsageMetricStatisticsEntityTenant;
import java.util.HashMap;
import java.util.List;

public class UsageMetricEntityMapper {

  public static UsageMetricStatisticsEntity toEntity(
      final List<UsageMetricStatisticsDbModel> dbModels) {

    var rpi = 0;
    var edi = 0;
    final var tenants = new HashMap<String, UsageMetricStatisticsEntityTenant>();
    for (final UsageMetricStatisticsDbModel dbModel : dbModels) {

      final var tenantId = dbModel.tenantId();
      final var value = dbModel.value();
      switch (dbModel.eventType()) {
        case RPI -> {
          rpi += value;
          tenants.merge(
              tenantId,
              new UsageMetricStatisticsEntityTenant(value, 0),
              UsageMetricStatisticsEntityTenant::merge);
        }
        case EDI -> {
          edi += value;
          tenants.merge(
              tenantId,
              new UsageMetricStatisticsEntityTenant(0, value),
              UsageMetricStatisticsEntityTenant::merge);
        }
        default -> throw new IllegalStateException("Unexpected value: " + dbModel.eventType());
      }
    }

    return new UsageMetricStatisticsEntity(rpi, edi, tenants.size(), tenants);
  }
}
