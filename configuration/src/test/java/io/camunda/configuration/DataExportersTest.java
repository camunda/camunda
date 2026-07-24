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
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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
class DataExportersTest {

  private ExporterCfg expectedExporterCfg;

  @BeforeEach
  void setUp() {
    expectedExporterCfg = new ExporterCfg();
    expectedExporterCfg.setClassName("class-name");
    expectedExporterCfg.setJarPath("jar-path");
    expectedExporterCfg.setArgs(Map.of("arg1", "value1", "arg2", "value2"));
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.exporters.foo.class-name=class-name",
        "camunda.data.exporters.foo.jar-path=jar-path",
        "camunda.data.exporters.foo.args.arg1=value1",
        "camunda.data.exporters.foo.args.arg2=value2"
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetExporters() {
      assertThat(brokerCfg.getExporters())
          .hasSize(2)
          .containsKeys("camundaexporter", "foo")
          .containsEntry("foo", expectedExporterCfg);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.exporters.foo.className=class-name",
        "zeebe.broker.exporters.foo.jarPath=jar-path",
        "zeebe.broker.exporters.foo.args.arg1=value1",
        "zeebe.broker.exporters.foo.args.arg2=value2",
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetExportersFromLegacy() {
      assertThat(brokerCfg.getExporters())
          .hasSize(2)
          .containsKeys("camundaexporter", "foo")
          .containsEntry("foo", expectedExporterCfg);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.data.exporters.foo.class-name=class-name",
        "camunda.data.exporters.foo.jar-path=jar-path",
        "camunda.data.exporters.foo.args.arg1=value1",
        "camunda.data.exporters.foo.args.arg2=value2",
        // legacy
        "zeebe.broker.exporters.foo.className=classNameLegacy",
        "zeebe.broker.exporters.foo.jarPath=jarPathLegacy",
        "zeebe.broker.exporters.foo.args.arg1=value1Legacy",
        "zeebe.broker.exporters.foo.args.arg2=value2Legacy",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetExportersFromNew() {
      assertThat(brokerCfg.getExporters())
          .hasSize(2)
          .containsKeys("camundaexporter", "foo")
          .containsEntry("foo", expectedExporterCfg);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // class has a registered merger (TestExporterConfigMergers.RecordingMerger)
        "camunda.data.exporters.foo.class-name=io.camunda.configuration.test.MergeableExporter",
        "camunda.data.exporters.foo.jar-path=jar-path",
        "camunda.data.exporters.foo.args.arg1=value1",
        "zeebe.broker.exporters.foo.className=io.camunda.configuration.test.MergeableExporter",
        "zeebe.broker.exporters.foo.jarPath=jar-path",
        "zeebe.broker.exporters.foo.args.arg1=value1Legacy",
        "zeebe.broker.exporters.foo.args.arg2=value2",
      })
  class WithRegisteredMergerMergesArgs {
    final BrokerBasedProperties brokerCfg;

    WithRegisteredMergerMergesArgs(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldMergeLegacyAndUnifiedArgsViaExporterConfigMerger() {
      final ExporterCfg exporterCfg = brokerCfg.getExporters().get("foo");
      assertThat(exporterCfg).isNotNull();
      assertThat(exporterCfg.getArgs())
          .containsEntry("arg1", "value1") // unified (overlay) wins the collision
          .containsEntry("arg2", "value2") // legacy (base) fills the gap
          .containsEntry(
              "mergedby", "test-merger"); // proves the SPI merger ran, not a full replace
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // class has NO registered merger
        "camunda.data.exporters.foo.class-name=io.camunda.configuration.test.NoMergerExporter",
        "camunda.data.exporters.foo.jar-path=jar-path",
        "camunda.data.exporters.foo.args.arg1=value1",
        "zeebe.broker.exporters.foo.className=io.camunda.configuration.test.NoMergerExporter",
        "zeebe.broker.exporters.foo.jarPath=jar-path",
        "zeebe.broker.exporters.foo.args.arg2=value2",
      })
  class WithNoMergerReplacesArgsWholesale {
    final BrokerBasedProperties brokerCfg;

    WithNoMergerReplacesArgsWholesale(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldReplaceArgsWholesaleWhenNoMergerShipsForClass() {
      final ExporterCfg exporterCfg = brokerCfg.getExporters().get("foo");
      assertThat(exporterCfg).isNotNull();
      assertThat(exporterCfg.getArgs())
          .containsEntry("arg1", "value1") // unified taken as-is
          .doesNotContainKey("arg2") // legacy dropped: no merger ⇒ whole-map replace
          .doesNotContainKey("mergedby"); // merger did not run
    }
  }
}
