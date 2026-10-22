/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class EventPlaneClusterKeyTest {

  private final BrokerCfg brokerCfg = new BrokerCfg();
  private final ClusterConfigurationService clusterConfiguration =
      mock(ClusterConfigurationService.class, RETURNS_DEEP_STUBS);
  private final TestPartitionTransitionContext context = new TestPartitionTransitionContext();

  @BeforeEach
  void setUp() {
    brokerCfg.getCluster().setClusterName("my-cluster");
    context.setBrokerCfg(brokerCfg);
    context.setClusterConfigurationService(clusterConfiguration);
  }

  @Test
  void shouldPreferTheEventPlanesClusterId() {
    // given
    brokerCfg.getExperimental().getEventPlane().setClusterId("event-plane-id");
    brokerCfg.getCluster().setClusterId("cluster-id");

    // then
    assertThat(EventPlaneClusterKey.of(context)).isEqualTo("event-plane-id");
    assertThat(EventPlaneClusterKey.configured(brokerCfg)).isEqualTo("event-plane-id");
  }

  @Test
  void shouldUseTheConfiguredClusterId() {
    // given
    brokerCfg.getCluster().setClusterId("cluster-id");

    // then
    assertThat(EventPlaneClusterKey.of(context)).isEqualTo("cluster-id");
    assertThat(EventPlaneClusterKey.configured(brokerCfg)).isEqualTo("cluster-id");
  }

  @Test
  void shouldUseTheGeneratedClusterIdWhenNoneIsConfigured() {
    // given
    when(clusterConfiguration.getCurrentClusterConfiguration().clusterId())
        .thenReturn(Optional.of("generated-id"));

    // then
    assertThat(EventPlaneClusterKey.of(context)).isEqualTo("generated-id");
    assertThat(EventPlaneClusterKey.configured(brokerCfg)).isNull();
  }

  @Test
  void shouldUseTheClusterNameWhenThereIsNoGeneratedClusterIdYet() {
    // given
    when(clusterConfiguration.getCurrentClusterConfiguration().clusterId())
        .thenReturn(Optional.empty());

    // then
    assertThat(EventPlaneClusterKey.of(context)).isEqualTo("my-cluster");
    assertThat(EventPlaneClusterKey.configured(brokerCfg)).isNull();
  }
}
