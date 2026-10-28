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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

/**
 * Registers metrics for the number of rows in each RDBMS table, per physical tenant. Each physical
 * tenant has its own database, so a separate gauge is registered per (physical tenant, table) and
 * tagged with the {@code physicalTenant} label, consistent with the other partition-scoped RDBMS
 * metrics. The actual (cached) row counts are provided by a per-tenant {@link
 * RdbmsTableRowCountProvider}.
 */
public class PhysicalTenantsRdbmsTableRowCountMetrics implements MeterBinder, AutoCloseable {

  private static final String NAMESPACE = "zeebe.rdbms";
  private static final String METRIC_NAME = NAMESPACE + ".table.row.count";
  private static final long UNKNOWN_ROW_COUNT = -1;

  private final Map<String, RdbmsTableRowCountProvider> rowCountProviders;
  private final List<ExecutorService> executors;
  private final Predicate<String> isCounted;

  /**
   * @param rowCountProviders the row count provider for each physical tenant, keyed by physical
   *     tenant id
   */
  public PhysicalTenantsRdbmsTableRowCountMetrics(
      final Map<String, RdbmsTableRowCountProvider> rowCountProviders) {
    this(rowCountProviders, List.of());
  }

  /**
   * @param rowCountProviders the row count provider for each physical tenant, keyed by physical
   *     tenant id
   * @param executors executors owned by these metrics and closed with the metrics bean
   */
  public PhysicalTenantsRdbmsTableRowCountMetrics(
      final Map<String, RdbmsTableRowCountProvider> rowCountProviders,
      final List<ExecutorService> executors) {
    this(rowCountProviders, executors, physicalTenantId -> true);
  }

  /**
   * @param rowCountProviders the row count provider for each physical tenant, keyed by physical
   *     tenant id
   * @param executors executors owned by these metrics and closed with the metrics bean
   * @param isCounted whether a physical tenant's rows are counted at the moment; one that is not
   *     reports an unknown row count rather than reaching its database
   */
  public PhysicalTenantsRdbmsTableRowCountMetrics(
      final Map<String, RdbmsTableRowCountProvider> rowCountProviders,
      final List<ExecutorService> executors,
      final Predicate<String> isCounted) {
    this.rowCountProviders = Map.copyOf(rowCountProviders);
    this.executors = List.copyOf(executors);
    this.isCounted = isCounted;
  }

  @Override
  public void bindTo(final MeterRegistry registry) {
    rowCountProviders.forEach(
        (physicalTenantId, rowCountProvider) -> {
          for (final String tableName : RdbmsTableNames.TABLE_NAMES) {
            Gauge.builder(
                    METRIC_NAME,
                    () ->
                        isCounted.test(physicalTenantId)
                            ? rowCountProvider.getRowCount(tableName)
                            : UNKNOWN_ROW_COUNT)
                .description("Number of rows in the RDBMS table")
                .tag(PartitionKeyNames.PHYSICAL_TENANT.asString(), physicalTenantId)
                .tag("table", tableName)
                .register(registry);
          }
        });
  }

  @Override
  public void close() {
    executors.forEach(ExecutorService::shutdown);
  }
}
