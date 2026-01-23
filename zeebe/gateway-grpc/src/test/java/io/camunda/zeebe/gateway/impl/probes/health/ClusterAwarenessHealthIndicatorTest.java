/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.Test;
import org.springframework.boot.health.contributor.Status;

public class ClusterAwarenessHealthIndicatorTest {

  @Test
  public void shouldRejectNullInConstructor() {
    // when + then
    assertThatThrownBy(() -> new ClusterAwarenessHealthIndicator(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldReportDownIfSupplierReturnsEmpty() {
    // given
    final Supplier<Optional<BrokerClusterState>> stateSupplier = () -> Optional.empty();
    final var sutHealthIndicator = new ClusterAwarenessHealthIndicator(stateSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  public void shouldReportUpIfListOfBrokersIsNotEmpty() {
    // given
    final BrokerClusterState mockClusterState = mock(BrokerClusterState.class);
    when(mockClusterState.getBrokers()).thenReturn(List.of(1));

    final Supplier<Optional<BrokerClusterState>> stateSupplier =
        () -> Optional.of(mockClusterState);
    final var sutHealthIndicator = new ClusterAwarenessHealthIndicator(stateSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  public void shouldReportDownIfListOfBrokersIsEmpty() {
    // given
    final BrokerClusterState mockClusterState = mock(BrokerClusterState.class);
    when(mockClusterState.getBrokers()).thenReturn(emptyList());

    final Supplier<Optional<BrokerClusterState>> stateSupplier =
        () -> Optional.of(mockClusterState);
    final var sutHealthIndicator = new ClusterAwarenessHealthIndicator(stateSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.DOWN);
  }
}
