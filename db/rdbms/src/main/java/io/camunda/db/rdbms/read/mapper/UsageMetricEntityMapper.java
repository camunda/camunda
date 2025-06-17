/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.UsageMetricDbModel.UsageMetricTenantStatisticsDbModel;
import io.camunda.search.entities.UsageMetricStatisticsEntity;
import io.camunda.search.entities.UsageMetricStatisticsEntity.UsageMetricStatisticsEntityTenant;
import io.camunda.search.entities.UsageMetricStatisticsEntity.UsageMetricStatisticsEntityTenant.Builder;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class UsageMetricEntityMapper {

  public static UsageMetricStatisticsEntity toEntity(
      final List<UsageMetricTenantStatisticsDbModel> dbModels) {

    long rpi = 0;
    long edi = 0;
    final var tenants = new HashMap<String, UsageMetricStatisticsEntityTenant>(dbModels.size());

    for (final UsageMetricTenantStatisticsDbModel dbModel : dbModels) {
      final long tenantRpi = Optional.ofNullable(dbModel.rpi()).orElse(0L);
      final long tenantEdi = Optional.ofNullable(dbModel.edi()).orElse(0L);

      rpi += tenantRpi;
      edi += tenantEdi;

      tenants.put(dbModel.tenantId(), new Builder().rpi(tenantRpi).edi(tenantEdi).build());
    }

    return new UsageMetricStatisticsEntity(rpi, edi, tenants.size(), tenants);
  }
}
