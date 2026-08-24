/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.RestoreProperties;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.DataDirectoryProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

final class RestoreAppMultiplePhysicalTenantsTest {

  @Test
  void shouldRejectRestoreWhenMultiplePhysicalTenantsAreConfigured() {
    // given
    final PhysicalTenantIds physicalTenantIds = () -> Set.of("default", "tenantA");

    // when / then
    assertThatThrownBy(() -> newRestoreApp(physicalTenantIds))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not supported by RestoreApp")
        .hasMessageContaining("/cluster/v2/restore");
  }

  private RestoreApp newRestoreApp(final PhysicalTenantIds physicalTenantIds) {
    return new RestoreApp(
        new Camunda(),
        new BrokerBasedProperties(),
        Mockito.mock(BackupStore.class),
        null,
        new RestoreProperties(false, List.of()),
        new SimpleMeterRegistry(),
        NodeIdProvider.staticProvider(1),
        Mockito.mock(DataDirectoryProvider.class),
        context -> {},
        (restoreId, nodeId) -> new RestoreApp.PreRestoreActionResult(false, ""),
        physicalTenantIds);
  }
}
