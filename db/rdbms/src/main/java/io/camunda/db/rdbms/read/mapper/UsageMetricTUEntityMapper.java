/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.UsageMetricTUDbModel.UsageMetricTUTenantStatisticsDbModel;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity.UsageMetricTUStatisticsEntityTenant;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity.UsageMetricTUStatisticsEntityTenant.Builder;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class UsageMetricTUEntityMapper {

  public static UsageMetricTUStatisticsEntity toEntity(
      final List<UsageMetricTUTenantStatisticsDbModel> dbModels, final Long totalTu) {

    final var tenants = new HashMap<String, UsageMetricTUStatisticsEntityTenant>(dbModels.size());

    for (final UsageMetricTUTenantStatisticsDbModel dbModel : dbModels) {
      final long tenantTu = Optional.ofNullable(dbModel.tu()).orElse(0L);
      tenants.put(dbModel.tenantId(), new Builder().tu(tenantTu).build());
    }

    return new UsageMetricTUStatisticsEntity(totalTu, tenants);
  }
}
