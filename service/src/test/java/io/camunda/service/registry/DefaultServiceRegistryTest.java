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
}
