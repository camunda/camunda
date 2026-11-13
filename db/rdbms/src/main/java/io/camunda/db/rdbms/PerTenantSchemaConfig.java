/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import java.time.Duration;
import javax.sql.DataSource;

/**
 * Bundles the per-physical-tenant facts that {@link LiquibaseSchemaManager} needs to run an
 * independent Liquibase migration for a single tenant.
 *
 * <p>This record lives in {@code db/rdbms} so the schema manager does not have to depend on the
 * higher-level modules ({@code dist}, {@code configuration}) that own the tenant-discovery and
 * data-source registry code. Callers in those modules (e.g. {@code MyBatisConfiguration}) build the
 * map keyed by physical-tenant id and hand it to {@link LiquibaseSchemaManager}.
 *
 * @param partitionState whether the tenant also keeps its partitions' state in this database, which
 *     then needs the state's tables too
 */
public record PerTenantSchemaConfig(
    DataSource dataSource,
    VendorDatabaseProperties vendorDatabaseProperties,
    String prefix,
    boolean autoDdl,
    Duration ddlLockWaitTimeout,
    boolean partitionState) {

  /** For a tenant that keeps its partitions' state elsewhere, for example in RocksDB. */
  public PerTenantSchemaConfig(
      final DataSource dataSource,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final String prefix,
      final boolean autoDdl,
      final Duration ddlLockWaitTimeout) {
    this(dataSource, vendorDatabaseProperties, prefix, autoDdl, ddlLockWaitTimeout, false);
  }
}
