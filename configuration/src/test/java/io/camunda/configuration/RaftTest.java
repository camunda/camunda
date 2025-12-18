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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class RaftTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.raft.preallocate-segment-files=true",
        "camunda.cluster.raft.segment-preallocation-strategy=POSIX"
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetSegmentPreallocationStrategy() {
      assertThat(brokerCfg.getExperimental().getRaft().getSegmentPreallocationStrategy())
          .isEqualTo(PreAllocateStrategy.POSIX);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.experimental.raft.preallocateSegmentFiles=true",
        "zeebe.broker.experimental.raft.segmentPreallocationStrategy=POSIX"
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetSegmentPreallocationStrategyFromLegacy() {
      assertThat(brokerCfg.getExperimental().getRaft().getSegmentPreallocationStrategy())
          .isEqualTo(PreAllocateStrategy.POSIX);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.cluster.raft.preallocate-segment-files=true",
        "camunda.cluster.raft.segment-preallocation-strategy=FILL",
        // legacy
        "zeebe.broker.experimental.raft.preallocateSegmentFiles=true",
        "zeebe.broker.experimental.raft.segmentPreallocationStrategy=POSIX"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldPreferNewSegmentPreallocationStrategy() {
      assertThat(brokerCfg.getExperimental().getRaft().getSegmentPreallocationStrategy())
          .isEqualTo(PreAllocateStrategy.FILL);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.raft.preallocate-segment-files=false",
        "camunda.cluster.raft.segment-preallocation-strategy=POSIX"
      })
  class WithPreallocationDisabled {
    final BrokerBasedProperties brokerCfg;

    WithPreallocationDisabled(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldForceStrategyToNoop() {
      assertThat(brokerCfg.getExperimental().getRaft().getSegmentPreallocationStrategy())
          .isEqualTo(PreAllocateStrategy.NOOP);
    }
  }

  @Nested
  class WithoutNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithoutNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldUseDefaultSegmentPreallocationStrategy() {
      assertThat(brokerCfg.getExperimental().getRaft().getSegmentPreallocationStrategy())
          .isEqualTo(PreAllocateStrategy.FILL);
    }
  }
}
