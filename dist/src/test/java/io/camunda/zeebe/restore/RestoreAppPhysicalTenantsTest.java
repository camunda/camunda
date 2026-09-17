/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.RestoreProperties;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.service.TenantRestoreEnvironment;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.DataDirectoryProvider;
import io.camunda.zeebe.restore.PhysicalTenantRestoreConfigurations.PhysicalTenantBrokerConfigurations;
import io.camunda.zeebe.restore.PhysicalTenantRestoreConfigurations.PhysicalTenantRestoreEnvironments;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

final class RestoreAppPhysicalTenantsTest {

  private static final String OTHER_TENANT = "tenanta";

  @Test
  void shouldAcceptAClusterWithSeveralPhysicalTenants() {
    // given / when / then — a multi-tenant cluster is restored by this application, no longer
    // rejected in favour of the v2 REST endpoints
    assertThatCode(() -> newRestoreApp(Map.of("default", 1, OTHER_TENANT, 1), null))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRequireExportedPositionsForEveryPhysicalTenantOnRdbms() {
    // given — RDBMS secondary storage, but only the default tenant has a mapper bundle
    final var bundles = Map.of("default", bundle());

    // when / then — restoring the other tenant would otherwise pick a restore point without
    // knowing how far its own exporter had got
    assertThatThrownBy(() -> newRestoreApp(Map.of("default", 1, OTHER_TENANT, 1), bundles))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ExporterPositionMapper")
        .hasMessageContaining(OTHER_TENANT);
  }

  @Test
  void shouldAcceptRdbmsWhenEveryPhysicalTenantHasExportedPositions() {
    // given
    final var bundles = Map.of("default", bundle(), OTHER_TENANT, bundle());

    // when / then
    assertThatCode(() -> newRestoreApp(Map.of("default", 1, OTHER_TENANT, 1), bundles))
        .doesNotThrowAnyException();
  }

  private static RdbmsMapperBundle bundle() {
    final var bundle = Mockito.mock(RdbmsMapperBundle.class);
    Mockito.when(bundle.exporterPositionMapper())
        .thenReturn(Mockito.mock(ExporterPositionMapper.class));
    return bundle;
  }

  /**
   * @param rdbmsMapperBundles when non-null, the secondary storage is configured as RDBMS and these
   *     are the bundles available; when null, it is left at its default (not RDBMS)
   */
  private RestoreApp newRestoreApp(
      final Map<String, Integer> partitionCountPerTenant,
      final Map<String, RdbmsMapperBundle> rdbmsMapperBundles) {
    final var camunda = new Camunda();
    if (rdbmsMapperBundles != null) {
      camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.rdbms);
    }

    final var root = new BrokerBasedProperties();
    final Map<String, BrokerCfg> configurations =
        partitionCountPerTenant.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> {
                      if ("default".equals(entry.getKey())) {
                        root.getCluster().setPartitionsCount(entry.getValue());
                        return root;
                      }
                      final var configuration = new BrokerCfg();
                      configuration.getCluster().setPartitionsCount(entry.getValue());
                      return configuration;
                    }));

    final var environments =
        partitionCountPerTenant.keySet().stream()
            .collect(
                Collectors.toMap(
                    tenantId -> tenantId,
                    tenantId -> new TenantRestoreEnvironment("elasticsearch", false)));

    return new RestoreApp(
        camunda,
        root,
        new PhysicalTenantBrokerConfigurations(configurations),
        new PhysicalTenantRestoreEnvironments(environments),
        new PhysicalTenantBackupStores(configurations),
        new RestoreArguments(),
        rdbmsMapperBundles,
        new RestoreProperties(false, List.of()),
        new SimpleMeterRegistry(),
        NodeIdProvider.staticProvider(1),
        Mockito.mock(DataDirectoryProvider.class),
        context -> {},
        (restoreId, nodeId) -> new RestoreApp.PreRestoreActionResult(false, ""));
  }
}
