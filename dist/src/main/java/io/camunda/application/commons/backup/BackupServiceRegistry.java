/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds one fully-wired {@link BackupService} per physical tenant together with the repository
 * props it was built from. Built once at startup (see {@link BackupServiceRegistryConfiguration})
 * and consumed by the {@code backupHistory} actuator and the standalone backup manager, both of
 * which fan cluster-wide backup operations out across every configured physical tenant.
 *
 * <p>Entries are indexed by physical tenant id for direct lookup ({@link
 * #forPhysicalTenant(String)}) and iterated in configuration order ({@link
 * #physicalTenantBackups()}) so fan-out and aggregation stay deterministic.
 */
public final class BackupServiceRegistry {

  private final Map<String, PhysicalTenantBackup> byPhysicalTenant;

  public BackupServiceRegistry(final List<PhysicalTenantBackup> physicalTenantBackups) {
    final var indexed = new LinkedHashMap<String, PhysicalTenantBackup>();
    physicalTenantBackups.forEach(backup -> indexed.put(backup.physicalTenantId(), backup));
    byPhysicalTenant = Collections.unmodifiableMap(indexed);
  }

  /** All configured physical-tenant backups, in configuration order. */
  public Collection<PhysicalTenantBackup> physicalTenantBackups() {
    return byPhysicalTenant.values();
  }

  /** The backup wiring for a single physical tenant. */
  public PhysicalTenantBackup forPhysicalTenant(final String physicalTenantId) {
    final var backup = byPhysicalTenant.get(physicalTenantId);
    if (backup == null) {
      throw new IllegalArgumentException("Unknown physical tenant id '" + physicalTenantId + "'");
    }
    return backup;
  }

  public boolean isEmpty() {
    return byPhysicalTenant.isEmpty();
  }

  /** A single physical tenant's backup service and the repository props it was wired with. */
  public record PhysicalTenantBackup(
      String physicalTenantId,
      BackupService backupService,
      BackupRepositoryProps repositoryProps) {}
}
