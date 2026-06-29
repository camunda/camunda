/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.read.RdbmsTenantReaders;
import io.camunda.db.rdbms.read.replication.ReplicationLogStatusProviderFactory;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.db.rdbms.write.RdbmsWriterFactory;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;

/**
 * Creates {@link RdbmsService} instances scoped to a single physical tenant. Each physical tenant
 * connects to its own RDBMS through its own datasource, so the service is built on demand once the
 * physical tenant is known (e.g. from the exporter {@code Context} at configure time) by combining
 * that tenant's {@link RdbmsMapperBundle} and {@link RdbmsTenantReaders}.
 */
public class RdbmsServiceFactory {
  private final Map<String, RdbmsMapperBundle> rdbmsMapperBundles;
  private final Map<String, RdbmsTenantReaders> rdbmsTenantReaders;

  public RdbmsServiceFactory(
      final Map<String, RdbmsMapperBundle> rdbmsMapperBundles,
      final Map<String, RdbmsTenantReaders> rdbmsTenantReaders) {
    this.rdbmsMapperBundles = rdbmsMapperBundles;
    this.rdbmsTenantReaders = rdbmsTenantReaders;
  }

  /**
   * Creates the tenant's service, registering its writer metrics against the given {@code
   * meterRegistry}. Callers pass the partition-scoped registry (e.g. the exporter {@code
   * Context}'s) so the writer metrics inherit its {@code partition} and {@code physicalTenant}
   * common tags.
   */
  public RdbmsService createRdbmsService(
      final String physicalTenantId, final MeterRegistry meterRegistry) {
    final var rdbmsMapperBundle = rdbmsMapperBundles.get(physicalTenantId);
    if (rdbmsMapperBundle == null) {
      throw new IllegalArgumentException(
          "Missing RDBMS mapper bundle for physical tenant '%s'".formatted(physicalTenantId));
    }
    final var rdbmsTenantReader = rdbmsTenantReaders.get(physicalTenantId);
    if (rdbmsTenantReader == null) {
      throw new IllegalArgumentException(
          "Missing RDBMS readers for physical tenant '%s'".formatted(physicalTenantId));
    }
    final var rdbmsWriterFactory = new RdbmsWriterFactory(rdbmsMapperBundle, meterRegistry);
    final var replicationLogStatusProviderFactory =
        new ReplicationLogStatusProviderFactory(
            rdbmsMapperBundle.vendorDatabaseProperties(),
            rdbmsMapperBundle.replicationStatusMapper());
    return new RdbmsService(
        rdbmsWriterFactory, rdbmsTenantReader, replicationLogStatusProviderFactory);
  }
}
