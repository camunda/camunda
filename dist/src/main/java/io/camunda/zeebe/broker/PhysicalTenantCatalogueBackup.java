/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg.BackupStoreFactory;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference.Owner;
import io.camunda.zeebe.dynamic.config.state.TenantAvailability;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the {@link PhysicalTenantCatalogue} in the backup store of the default tenant up to date:
 * on the coordinator, every change of the cluster configuration that adds, changes or removes a
 * tenant created through the API writes the catalogue again. Without a backup store for the default
 * tenant there is nowhere to keep it, and nothing is written.
 */
@NullMarked
public final class PhysicalTenantCatalogueBackup implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantCatalogueBackup.class);

  private final ExecutorService writer =
      Executors.newSingleThreadExecutor(
          Thread.ofPlatform().name("physical-tenant-catalogue-backup").daemon().factory());
  private final MemberId localMemberId;
  private final @Nullable BackupStore store;
  private @Nullable Map<String, String> written;
  private @Nullable ClusterConfigurationService service;

  public PhysicalTenantCatalogueBackup(final Broker broker, final MemberId localMemberId) {
    this.localMemberId = localMemberId;
    final var defaultTenant =
        broker.getSystemContext().getPhysicalTenantContexts().get(DEFAULT_PHYSICAL_TENANT_ID);
    store =
        defaultTenant == null
            ? null
            : BackupStoreFactory.createStore(defaultTenant.config().getData().getBackup());
    if (store == null) {
      LOG.info(
          "The default physical tenant has no backup store; tenants created through the API are"
              + " not kept in backups and a restore cannot bring them back");
      return;
    }
    broker
        .start()
        .thenRunAsync(
            () -> {
              service = broker.getBrokerContext().getClusterConfigurationService();
              service.addUpdateListener(
                  configuration -> writer.execute(() -> write(configuration)));
              write(service.getCurrentClusterConfiguration());
            },
            writer);
  }

  private void write(final CurrentClusterConfiguration configuration) {
    final var current = service;
    if (store == null || current == null || !isCoordinator(configuration)) {
      return;
    }
    final Map<String, String> versions = new TreeMap<>();
    final Map<String, PhysicalTenantConfigReference> references = new TreeMap<>();
    configuration
        .partitionGroups()
        .forEach(
            (tenantId, group) ->
                group
                    .tenantConfig()
                    .filter(reference -> reference.owner() == Owner.API)
                    .filter(
                        reference ->
                            group.availability().state() == TenantAvailability.State.ENABLED)
                    .ifPresent(
                        reference -> {
                          references.put(tenantId, reference);
                          versions.put(
                              tenantId,
                              reference.version()
                                  + "/"
                                  + reference.sha256()
                                  + "/"
                                  + reference.incarnationId());
                        }));
    if (versions.equals(written)) {
      return;
    }
    try {
      final List<PhysicalTenantCatalogue.Entry> entries = new ArrayList<>();
      for (final var tenant : references.entrySet()) {
        final var tenantId = tenant.getKey();
        final var reference = tenant.getValue();
        final var blob =
            current.fetchPhysicalTenantConfig(tenantId, reference).get(30, TimeUnit.SECONDS);
        entries.add(
            new PhysicalTenantCatalogue.Entry(
                tenantId,
                reference.version(),
                reference.sha256(),
                reference.incarnationId(),
                blob));
      }
      store
          .storeBackupMetadata(
              PhysicalTenantCatalogue.METADATA_SLOT, new PhysicalTenantCatalogue(entries).toBytes())
          .get(30, TimeUnit.SECONDS);
      written = versions;
      LOG.info("Wrote the catalogue of physical tenants created through the API: {}", versions);
    } catch (final Exception e) {
      LOG.warn(
          "Failed to write the catalogue of physical tenants created through the API; it is"
              + " written again with the next change of the cluster configuration",
          e);
    }
  }

  private boolean isCoordinator(final CurrentClusterConfiguration configuration) {
    return localMemberId.equals(
        ClusterConfigurationCoordinatorSupplier.from(() -> configuration).getDefaultCoordinator());
  }

  @Override
  public void close() {
    writer.shutdownNow();
    if (store != null) {
      store.closeAsync().join();
    }
  }
}
