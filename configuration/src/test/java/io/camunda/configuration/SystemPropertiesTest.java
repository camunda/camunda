/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.ActorClockControlledPropertiesOverride;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beanoverrides.IdleStrategyPropertiesOverride;
import io.camunda.configuration.beanoverrides.RestorePropertiesOverride;
import io.camunda.configuration.beans.ActorClockControlledProperties;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.IdleStrategyBasedProperties;
import io.camunda.configuration.beans.RestoreProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  ActorClockControlledPropertiesOverride.class,
  IdleStrategyPropertiesOverride.class,
  RestorePropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class SystemPropertiesTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.system.cpu-thread-count=4",
        "camunda.system.io-thread-count=8",
        "camunda.system.clock-controlled=true",
        "camunda.system.upgrade.enable-version-check=false",
        "camunda.system.actor.idle.max-spins=1000",
        "camunda.system.actor.idle.max-yields=500",
        "camunda.system.actor.idle.min-park-period=10ms",
        "camunda.system.actor.idle.max-park-period=100ms",
        "camunda.system.restore.validate-config=false",
        "camunda.system.restore.ignore-files-in-target=lost+found,temp,cache"
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;
    final ActorClockControlledProperties clockCfg;
    final IdleStrategyBasedProperties idleCfg;
    final RestoreProperties restoreCfg;

    WithOnlyUnifiedConfigSet(
        @Autowired final BrokerBasedProperties brokerCfg,
        @Autowired final ActorClockControlledProperties clockCfg,
        @Autowired final IdleStrategyBasedProperties idleCfg,
        @Autowired final RestoreProperties restoreCfg) {
      this.brokerCfg = brokerCfg;
      this.clockCfg = clockCfg;
      this.idleCfg = idleCfg;
      this.restoreCfg = restoreCfg;
    }

    @Test
    void shouldSetCpuThreadCount() {
      assertThat(brokerCfg.getThreads().getCpuThreadCount()).isEqualTo(4);
    }

    @Test
    void shouldSetIoThreadCount() {
      assertThat(brokerCfg.getThreads().getIoThreadCount()).isEqualTo(8);
    }

    @Test
    void shouldSetClockControlled() {
      assertThat(clockCfg.controlled()).isTrue();
    }

    @Test
    void shouldSetEnableVersionCheck() {
      assertThat(brokerCfg.getExperimental().isVersionCheckRestrictionEnabled()).isFalse();
    }

    @Test
    void shouldSetActorIdleMaxSpins() {
      assertThat(idleCfg.maxSpins()).isEqualTo(1000L);
    }

    @Test
    void shouldSetActorIdleMaxYields() {
      assertThat(idleCfg.maxYields()).isEqualTo(500L);
    }

    @Test
    void shouldSetActorIdleMinParkPeriod() {
      assertThat(idleCfg.minParkPeriod()).isEqualTo(Duration.ofMillis(10));
    }

    @Test
    void shouldSetActorIdleMaxParkPeriod() {
      assertThat(idleCfg.maxParkPeriod()).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    void shouldSetRestoreValidateConfig() {
      assertThat(restoreCfg.validateConfig()).isFalse();
    }

    @Test
    void shouldSetRestoreIgnoreFilesInTarget() {
      assertThat(restoreCfg.ignoreFilesInTarget())
          .isEqualTo(List.of("lost+found", "temp", "cache"));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.threads.cpuThreadCount=6",
        "zeebe.broker.threads.ioThreadCount=12",
        "zeebe.clock.controlled=true",
        "zeebe.broker.experimental.versionCheckRestrictionEnabled=false",
        "zeebe.actor.idle.maxSpins=2000",
        "zeebe.actor.idle.maxYields=1000",
        "zeebe.actor.idle.minParkPeriod=20ms",
        "zeebe.actor.idle.maxParkPeriod=200ms",
        "zeebe.restore.validateConfig=false",
        "zeebe.restore.ignoreFilesInTarget=backup,old-data"
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;
    final ActorClockControlledProperties clockCfg;
    final IdleStrategyBasedProperties idleCfg;
    final RestoreProperties restoreCfg;

    WithOnlyLegacySet(
        @Autowired final BrokerBasedProperties brokerCfg,
        @Autowired final ActorClockControlledProperties clockCfg,
        @Autowired final IdleStrategyBasedProperties idleCfg,
        @Autowired final RestoreProperties restoreCfg) {
      this.brokerCfg = brokerCfg;
      this.clockCfg = clockCfg;
      this.idleCfg = idleCfg;
      this.restoreCfg = restoreCfg;
    }

    @Test
    void shouldSetCpuThreadCount() {
      assertThat(brokerCfg.getThreads().getCpuThreadCount()).isEqualTo(6);
    }

    @Test
    void shouldSetIoThreadCount() {
      assertThat(brokerCfg.getThreads().getIoThreadCount()).isEqualTo(12);
    }

    @Test
    void shouldSetClockControlled() {
      assertThat(clockCfg.controlled()).isTrue();
    }

    @Test
    void shouldSetEnableVersionCheck() {
      assertThat(brokerCfg.getExperimental().isVersionCheckRestrictionEnabled()).isFalse();
    }

    @Test
    void shouldSetActorIdleMaxSpins() {
      assertThat(idleCfg.maxSpins()).isEqualTo(2000L);
    }

    @Test
    void shouldSetActorIdleMaxYields() {
      assertThat(idleCfg.maxYields()).isEqualTo(1000L);
    }

    @Test
    void shouldSetActorIdleMinParkPeriod() {
      assertThat(idleCfg.minParkPeriod()).isEqualTo(Duration.ofMillis(20));
    }

    @Test
    void shouldSetActorIdleMaxParkPeriod() {
      assertThat(idleCfg.maxParkPeriod()).isEqualTo(Duration.ofMillis(200));
    }

    @Test
    void shouldSetRestoreValidateConfig() {
      assertThat(restoreCfg.validateConfig()).isFalse();
    }

    @Test
    void shouldSetRestoreIgnoreFilesInTarget() {
      assertThat(restoreCfg.ignoreFilesInTarget()).isEqualTo(List.of("backup", "old-data"));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new properties
        "camunda.system.cpu-thread-count=4",
        "camunda.system.io-thread-count=8",
        "camunda.system.clock-controlled=true",
        "camunda.system.upgrade.enable-version-check=false",
        "camunda.system.actor.idle.max-spins=1000",
        "camunda.system.actor.idle.max-yields=500",
        "camunda.system.actor.idle.min-park-period=10ms",
        "camunda.system.actor.idle.max-park-period=100ms",
        "camunda.system.restore.validate-config=false",
        "camunda.system.restore.ignore-files-in-target=new-ignore",
        // legacy properties (should be ignored when new ones are present)
        "zeebe.broker.threads.cpuThreadCount=10",
        "zeebe.broker.threads.ioThreadCount=20",
        "zeebe.clock.controlled=false",
        "zeebe.broker.experimental.versionCheckRestrictionEnabled=true",
        "zeebe.actor.idle.maxSpins=5000",
        "zeebe.actor.idle.maxYields=2500",
        "zeebe.actor.idle.minParkPeriod=50ms",
        "zeebe.actor.idle.maxParkPeriod=500ms",
        "zeebe.restore.validateConfig=true",
        "zeebe.restore.ignoreFilesInTarget=legacy-ignore"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;
    final ActorClockControlledProperties clockCfg;
    final IdleStrategyBasedProperties idleCfg;
    final RestoreProperties restoreCfg;

    WithNewAndLegacySet(
        @Autowired final BrokerBasedProperties brokerCfg,
        @Autowired final ActorClockControlledProperties clockCfg,
        @Autowired final IdleStrategyBasedProperties idleCfg,
        @Autowired final RestoreProperties restoreCfg) {
      this.brokerCfg = brokerCfg;
      this.clockCfg = clockCfg;
      this.idleCfg = idleCfg;
      this.restoreCfg = restoreCfg;
    }

    @Test
    void shouldSetCpuThreadCountFromNew() {
      assertThat(brokerCfg.getThreads().getCpuThreadCount()).isEqualTo(4);
    }

    @Test
    void shouldSetIoThreadCountFromNew() {
      assertThat(brokerCfg.getThreads().getIoThreadCount()).isEqualTo(8);
    }

    @Test
    void shouldSetClockControlledFromNew() {
      assertThat(clockCfg.controlled()).isTrue();
    }

    @Test
    void shouldSetEnableVersionCheckFromNew() {
      assertThat(brokerCfg.getExperimental().isVersionCheckRestrictionEnabled()).isFalse();
    }

    @Test
    void shouldSetActorIdleMaxSpinsFromNew() {
      assertThat(idleCfg.maxSpins()).isEqualTo(1000L);
    }

    @Test
    void shouldSetActorIdleMaxYieldsFromNew() {
      assertThat(idleCfg.maxYields()).isEqualTo(500L);
    }

    @Test
    void shouldSetActorIdleMinParkPeriodFromNew() {
      assertThat(idleCfg.minParkPeriod()).isEqualTo(Duration.ofMillis(10));
    }

    @Test
    void shouldSetActorIdleMaxParkPeriodFromNew() {
      assertThat(idleCfg.maxParkPeriod()).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    void shouldSetRestoreValidateConfigFromNew() {
      assertThat(restoreCfg.validateConfig()).isFalse();
    }

    @Test
    void shouldSetRestoreIgnoreFilesInTargetFromNew() {
      assertThat(restoreCfg.ignoreFilesInTarget()).isEqualTo(List.of("new-ignore"));
    }
  }
}
