/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.RdbmsTableNames;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.Map;

/**
 * Registers metrics for the number of rows in each RDBMS table, per physical tenant. Each physical
 * tenant has its own database, so a separate gauge is registered per (physical tenant, table) and
 * tagged with the {@code physicalTenant} label, consistent with the other partition-scoped RDBMS
 * metrics. The actual (cached) row counts are provided by a per-tenant {@link
 * RdbmsTableRowCountProvider}.
 */
public class PhysicalTenantsRdbmsTableRowCountMetrics implements MeterBinder {

  private static final String NAMESPACE = "zeebe.rdbms";
  private static final String METRIC_NAME = NAMESPACE + ".table.row.count";

  private final Map<String, RdbmsTableRowCountProvider> rowCountProviders;

  /**
   * @param rowCountProviders the row count provider for each physical tenant, keyed by physical
   *     tenant id
   */
  public PhysicalTenantsRdbmsTableRowCountMetrics(
      final Map<String, RdbmsTableRowCountProvider> rowCountProviders) {
    this.rowCountProviders = Map.copyOf(rowCountProviders);
  }

  @Override
  public void bindTo(final MeterRegistry registry) {
    rowCountProviders.forEach(
        (physicalTenantId, rowCountProvider) -> {
          for (final String tableName : RdbmsTableNames.TABLE_NAMES) {
            Gauge.builder(METRIC_NAME, () -> rowCountProvider.getRowCount(tableName))
                .description("Number of rows in the RDBMS table")
                .tag(PartitionKeyNames.PHYSICAL_TENANT.asString(), physicalTenantId)
                .tag("table", tableName)
                .register(registry);
          }
        });
  }
}
