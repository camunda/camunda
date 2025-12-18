/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.ExperimentalRaftCfg.PreAllocateStrategy;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class RaftTest {

  private final ApplicationContextRunner brokerRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              UnifiedConfiguration.class,
              BrokerBasedPropertiesOverride.class,
              UnifiedConfigurationHelper.class)
          .withPropertyValues("spring.profiles.active=broker");

  private static Stream<Arguments> segmentPreallocationStrategies() {
    return Stream.of(PreAllocateStrategy.values()).map(s -> Arguments.of(s.name(), s));
  }

  @Test
  void shouldSetSegmentPreallocationStrategyFromUnifiedConfig() {
    brokerRunner
        .withPropertyValues(
            "camunda.cluster.raft.preallocate-segment-files=true",
            "camunda.cluster.raft.segment-preallocation-strategy=POSIX")
        .run(
            context -> {
              final var brokerCfg = context.getBean(BrokerBasedProperties.class);
              assertThat(brokerCfg.getExperimental().getRaft().getSegmentPreallocationStrategy())
                  .isEqualTo(PreAllocateStrategy.POSIX);
            });
  }

  @Test
  void shouldSetSegmentPreallocationStrategyFromLegacyConfig() {
    brokerRunner
        .withPropertyValues(
            "zeebe.broker.experimental.raft.preallocateSegmentFiles=true",
            "zeebe.broker.experimental.raft.segmentPreallocationStrategy=POSIX")
        .run(
            context -> {
              final var brokerCfg = context.getBean(BrokerBasedProperties.class);
              assertThat(brokerCfg.getExperimental().getRaft().getSegmentPreallocationStrategy())
                  .isEqualTo(PreAllocateStrategy.POSIX);
            });
  }

  @Test
  void shouldPreferNewSegmentPreallocationStrategyWhenBothAreSet() {
    brokerRunner
        .withPropertyValues(
            // new
            "camunda.cluster.raft.preallocate-segment-files=true",
            "camunda.cluster.raft.segment-preallocation-strategy=FILL",
            // legacy
            "zeebe.broker.experimental.raft.preallocateSegmentFiles=true",
            "zeebe.broker.experimental.raft.segmentPreallocationStrategy=POSIX")
        .run(
            context -> {
              final var brokerCfg = context.getBean(BrokerBasedProperties.class);
              assertThat(brokerCfg.getExperimental().getRaft().getSegmentPreallocationStrategy())
                  .isEqualTo(PreAllocateStrategy.FILL);
            });
  }

  @Test
  void shouldForceStrategyToNoopWhenPreallocationDisabled() {
    brokerRunner
        .withPropertyValues(
            "camunda.cluster.raft.preallocate-segment-files=false",
            "camunda.cluster.raft.segment-preallocation-strategy=POSIX")
        .run(
            context -> {
              final var brokerCfg = context.getBean(BrokerBasedProperties.class);
              assertThat(brokerCfg.getExperimental().getRaft().getSegmentPreallocationStrategy())
                  .isEqualTo(PreAllocateStrategy.NOOP);
            });
  }

  @Test
  void shouldUseDefaultSegmentPreallocationStrategy() {
    brokerRunner.run(
        context -> {
          final var brokerCfg = context.getBean(BrokerBasedProperties.class);
          assertThat(brokerCfg.getExperimental().getRaft().getSegmentPreallocationStrategy())
              .isEqualTo(PreAllocateStrategy.FILL);
        });
  }

  @ParameterizedTest(name = "unified strategy '{0}' maps to {1}")
  @MethodSource("segmentPreallocationStrategies")
  void shouldMapUnifiedStrategyStringToEnum(
      final String strategy, final PreAllocateStrategy expected) {
    brokerRunner
        .withPropertyValues(
            "camunda.cluster.raft.preallocate-segment-files=true",
            "camunda.cluster.raft.segment-preallocation-strategy=" + strategy)
        .run(
            context -> {
              final var brokerCfg = context.getBean(BrokerBasedProperties.class);
              assertThat(brokerCfg.getExperimental().getRaft().getSegmentPreallocationStrategy())
                  .isEqualTo(expected);
            });
  }

  @ParameterizedTest(name = "legacy strategy '{0}' maps to {1}")
  @MethodSource("segmentPreallocationStrategies")
  void shouldMapLegacyStrategyStringToEnum(
      final String strategy, final PreAllocateStrategy expected) {
    brokerRunner
        .withPropertyValues(
            "zeebe.broker.experimental.raft.preallocateSegmentFiles=true",
            "zeebe.broker.experimental.raft.segmentPreallocationStrategy=" + strategy)
        .run(
            context -> {
              final var brokerCfg = context.getBean(BrokerBasedProperties.class);
              assertThat(brokerCfg.getExperimental().getRaft().getSegmentPreallocationStrategy())
                  .isEqualTo(expected);
            });
  }
}
