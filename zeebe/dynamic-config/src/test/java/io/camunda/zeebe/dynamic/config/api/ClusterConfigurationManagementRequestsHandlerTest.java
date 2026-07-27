/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeResult;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ClusterConfigurationManagementRequestsHandlerTest {

  private static final MemberId LOCAL_MEMBER = MemberId.from("0");

  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final ConfigurationChangeCoordinator coordinator =
      mock(ConfigurationChangeCoordinator.class);
  private final ClusterConfigurationManagementRequestsHandler handler =
      new ClusterConfigurationManagementRequestsHandler(
          coordinator, LOCAL_MEMBER, executor, new RequestValidatorRegistry());

  private static RestoreRequest restoreRequest(final boolean dryRun) {
    return new RestoreRequest(
        PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
        List.of(1L),
        null,
        null,
        "elasticsearch",
        false,
        dryRun);
  }

  @Test
  void shouldApplyOperationsWhenNotDryRun() {
    // given
    final var config = ClusterConfiguration.init();
    when(coordinator.applyOperations(any()))
        .thenReturn(
            CompletableActorFuture.completed(
                new ConfigurationChangeResult(config, config, 1L, List.of())));

    // when
    final ActorFuture<ClusterConfigurationChangeResponse> result =
        handler.restore(restoreRequest(false));

    // then
    assertThat(result.join().changeId()).isOne();
    verify(coordinator).applyOperations(any());
  }

  @Test
  void shouldSimulateOperationsWhenDryRun() {
    // given
    final var config = ClusterConfiguration.init();
    when(coordinator.simulateOperations(any()))
        .thenReturn(
            CompletableActorFuture.completed(
                new ConfigurationChangeResult(config, config, 1L, List.of())));

    // when
    final ActorFuture<ClusterConfigurationChangeResponse> result =
        handler.restore(restoreRequest(true));

    // then
    assertThat(result.join().changeId()).isOne();
    verify(coordinator).simulateOperations(any());
  }
}
