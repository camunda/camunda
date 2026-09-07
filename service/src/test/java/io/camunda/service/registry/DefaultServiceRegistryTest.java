/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import io.camunda.service.ClusterHistoryBackupServices;
import io.camunda.service.GroupServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import org.junit.jupiter.api.Test;

class DefaultServiceRegistryTest {

  /**
   * The controller's {@code @RequiresSecondaryStorage} gate normally answers first, but it decides
   * on the request's physical tenant while this service's existence is decided on the cluster-wide
   * storage type. A deployment that sets those inconsistently reaches the accessor, and must still
   * be told what is true of it rather than handed a 500.
   */
  @Test
  void shouldRejectClusterHistoryBackupsAsForbiddenWhenTheServiceIsAbsent() {
    // given a cluster whose secondary storage cannot serve history backups
    final var registry = DefaultServiceRegistry.of(builder -> {});

    // when / then
    assertThatExceptionOfType(ServiceException.class)
        .isThrownBy(registry::clusterHistoryBackupServices)
        .satisfies(
            e -> {
              assertThat(e.getStatus()).isEqualTo(Status.FORBIDDEN);
              assertThat(e.getMessage()).contains("history backups");
            });
  }

  @Test
  void shouldReturnClusterHistoryBackupsWhenTheServiceIsPresent() {
    // given
    final var services = mock(ClusterHistoryBackupServices.class);
    final var registry =
        DefaultServiceRegistry.of(builder -> builder.clusterHistoryBackupServices(services));

    // when / then
    assertThat(registry.clusterHistoryBackupServices()).isSameAs(services);
  }

  /**
   * A tenant-scoped accessor is reached with whatever physical tenant id was stamped on the
   * request, and that id is taken from the request path without being validated against the
   * configured tenants (ADR-0003). Rejecting an unknown one here is therefore the point at which an
   * arbitrary id stops, and callers rely on that: {@code DefaultMembershipService} keys a
   * per-outage map by the tenant in context, and only stays bounded by the configured tenants
   * because an unknown one never gets past this accessor to fail transiently.
   *
   * <p>Falling back to the default tenant instead would also be an isolation fault in its own
   * right, serving one tenant's data under another's id.
   */
  @Test
  void shouldRejectAnUnknownPhysicalTenant() {
    // given a registry that knows one tenant
    final var registry =
        DefaultServiceRegistry.of(
            builder -> builder.groupServices("tenanta", mock(GroupServices.class)));

    // when / then — asking for another is an error, not a fallback
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> registry.groupServices("tenantz"))
        .withMessageContaining("tenantz");
  }

  @Test
  void shouldReturnTheServicesOfTheRequestedPhysicalTenant() {
    // given two tenants with distinct instances
    final var tenantA = mock(GroupServices.class);
    final var tenantB = mock(GroupServices.class);
    final var registry =
        DefaultServiceRegistry.of(
            builder -> builder.groupServices("tenanta", tenantA).groupServices("tenantb", tenantB));

    // when / then — each id resolves to its own, so a rejection above cannot be mistaken for the
    // accessor simply never returning anything
    assertThat(registry.groupServices("tenanta")).isSameAs(tenantA);
    assertThat(registry.groupServices("tenantb")).isSameAs(tenantB);
  }
}
