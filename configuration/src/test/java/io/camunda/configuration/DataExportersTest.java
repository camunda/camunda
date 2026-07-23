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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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

  @Nested
  @TestPropertySource(
      properties = {
        // no secondary-storage type set ⇒ default (elasticsearch, document-based) autoconfigures
        // "camundaexporter" (base args) BEFORE populateFromExporters runs. Declaring the same
        // exporter here with a matching class-name makes it also flow through the unified/legacy
        // merge path, with the real io.camunda.exporter.config.CamundaExporterConfigMerger
        // (on the classpath via camunda-exporter) claiming "io.camunda.exporter.CamundaExporter".
        "camunda.data.exporters.camundaexporter.class-name=io.camunda.exporter.CamundaExporter",
        "camunda.data.exporters.camundaexporter.args.connect.cluster-name=my-custom-cluster",
      })
  class WithCamundaExporterAutoconfigAndUnifiedOverride {
    final BrokerBasedProperties brokerCfg;

    WithCamundaExporterAutoconfigAndUnifiedOverride(
        @Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldMergeAutoconfiguredArgsWithUnifiedOverrideInsteadOfReplacing() {
      final ExporterCfg exporterCfg = brokerCfg.getExporters().get("camundaexporter");
      assertThat(exporterCfg).isNotNull();

      final Map<String, Object> args = exporterCfg.getArgs();
      // proves it's a merge, not a wholesale replace: the autoconfigured args carry many more
      // top-level keys (index, bulk, history, ...) than the single one the override declares
      assertThat(args.size()).isGreaterThan(1);
      assertThat(args).containsKey("connect");

      @SuppressWarnings("unchecked")
      final Map<String, Object> connect = (Map<String, Object>) args.get("connect");
      assertThat(connect)
          .containsEntry("clustername", "my-custom-cluster") // unified (overlay) wins
          .containsEntry("type", "elasticsearch"); // autoconfig-derived key survives the merge
    }
  }

  @Nested
  class WithMultipleMergersClaimingSameExporterClass {

    private final ApplicationContextRunner brokerRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                UnifiedConfiguration.class,
                BrokerBasedPropertiesOverride.class,
                UnifiedConfigurationHelper.class)
            .withPropertyValues(
                "spring.profiles.active=broker",
                // unified
                "camunda.data.exporters.foo.class-name=io.camunda.configuration.test.DuplicateClaimedExporter",
                "camunda.data.exporters.foo.args.arg1=value1",
                // legacy
                "zeebe.broker.exporters.foo.className=io.camunda.configuration.test.DuplicateClaimedExporter",
                "zeebe.broker.exporters.foo.args.arg2=value2");

    @Test
    void shouldFailFastWhenMultipleMergersClaimTheSameExporterClass() {
      brokerRunner.run(
          context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                .hasRootCauseInstanceOf(UnifiedConfigurationException.class)
                .rootCause()
                .hasMessageContaining("Multiple ExporterConfigMerger")
                .hasMessageContaining("io.camunda.configuration.test.DuplicateClaimedExporter");
          });
    }
  }

  @Nested
  class WithMergerThatThrows {

    private final ApplicationContextRunner brokerRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                UnifiedConfiguration.class,
                BrokerBasedPropertiesOverride.class,
                UnifiedConfigurationHelper.class)
            .withPropertyValues(
                "spring.profiles.active=broker",
                // unified
                "camunda.data.exporters.foo.class-name=io.camunda.configuration.test.FailingMergeExporter",
                "camunda.data.exporters.foo.args.arg1=value1",
                // legacy
                "zeebe.broker.exporters.foo.className=io.camunda.configuration.test.FailingMergeExporter",
                "zeebe.broker.exporters.foo.args.arg2=value2");

    @Test
    void shouldWrapMergerRuntimeExceptionInUnifiedConfigurationException() {
      brokerRunner.run(
          context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("intentional test merge failure");

            final Throwable unifiedConfigurationException =
                findThrowableOfType(
                    context.getStartupFailure(), UnifiedConfigurationException.class);
            assertThat(unifiedConfigurationException)
                .isNotNull()
                .hasMessageContaining("Failed to merge exporter args for exporter 'foo'");
          });
    }

    private Throwable findThrowableOfType(final Throwable root, final Class<?> type) {
      Throwable current = root;
      while (current != null) {
        if (type.isInstance(current)) {
          return current;
        }
        current = current.getCause();
      }
      return null;
    }
  }
}
