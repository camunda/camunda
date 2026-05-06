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
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

class DataToolsTest {

  @Test
  void shouldHaveDefaults() {
    final Tools tools = new Tools();

    assertThat(tools.getExtensionPropertyToolName())
        .isEqualTo(Tools.DEFAULT_EXTENSION_PROPERTY_TOOL_NAME);
    assertThat(tools.getExtensionPropertyInboundConnectorType())
        .isEqualTo(Tools.DEFAULT_EXTENSION_PROPERTY_INBOUND_CONNECTOR_TYPE);
    assertThat(tools.getExtensionPropertyPrefixToolProperties())
        .isEqualTo(Tools.DEFAULT_EXTENSION_PROPERTY_PREFIX_TOOL_PROPERTIES);
  }

  @Nested
  @ActiveProfiles("broker")
  @SpringJUnitConfig({
    UnifiedConfiguration.class,
    UnifiedConfigurationHelper.class,
    BrokerBasedPropertiesOverride.class,
  })
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        "camunda.data.tools.extension-property-tool-name=custom.tool:name",
        "camunda.data.tools.extension-property-inbound-connector-type=custom.inbound.type",
        "camunda.data.tools.extension-property-prefix-tool-properties=custom.tool:",
      })
  class CamundaExporterWithCustomTools {
    @Test
    void shouldApplyToolsConfigToCamundaExporter(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      final var exporter = brokerBasedProperties.getCamundaExporter();
      final var config =
          ExporterConfiguration.fromArgs(
              io.camunda.exporter.config.ExporterConfiguration.class, exporter.getArgs());

      assertThat(config.getTools().getExtensionPropertyToolName()).isEqualTo("custom.tool:name");
      assertThat(config.getTools().getExtensionPropertyInboundConnectorType())
          .isEqualTo("custom.inbound.type");
      assertThat(config.getTools().getExtensionPropertyPrefixToolProperties())
          .isEqualTo("custom.tool:");
    }
  }

  @Nested
  @ActiveProfiles("broker")
  @SpringJUnitConfig({
    UnifiedConfiguration.class,
    UnifiedConfigurationHelper.class,
    BrokerBasedPropertiesOverride.class,
  })
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
      })
  class CamundaExporterWithDefaultTools {
    @Test
    void shouldApplyDefaultToolsConfigToCamundaExporter(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      final var exporter = brokerBasedProperties.getCamundaExporter();
      final var config =
          ExporterConfiguration.fromArgs(
              io.camunda.exporter.config.ExporterConfiguration.class, exporter.getArgs());

      assertThat(config.getTools().getExtensionPropertyToolName())
          .isEqualTo(Tools.DEFAULT_EXTENSION_PROPERTY_TOOL_NAME);
      assertThat(config.getTools().getExtensionPropertyInboundConnectorType())
          .isEqualTo(Tools.DEFAULT_EXTENSION_PROPERTY_INBOUND_CONNECTOR_TYPE);
      assertThat(config.getTools().getExtensionPropertyPrefixToolProperties())
          .isEqualTo(Tools.DEFAULT_EXTENSION_PROPERTY_PREFIX_TOOL_PROPERTIES);
    }
  }

  @Nested
  @ActiveProfiles("broker")
  @SpringJUnitConfig({
    UnifiedConfiguration.class,
    UnifiedConfigurationHelper.class,
    BrokerBasedPropertiesOverride.class,
  })
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
        "camunda.data.tools.extension-property-tool-name=custom.tool:name",
        "camunda.data.tools.extension-property-inbound-connector-type=custom.inbound.type",
        "camunda.data.tools.extension-property-prefix-tool-properties=custom.tool:",
      })
  class RdbmsExporterWithCustomTools {
    @Test
    void shouldApplyToolsConfigToRdbmsExporter(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      final var exporter = brokerBasedProperties.getRdbmsExporter();
      final var config =
          ExporterConfiguration.fromArgs(
              io.camunda.exporter.rdbms.ExporterConfiguration.class, exporter.getArgs());

      assertThat(config.getTools().getExtensionPropertyToolName()).isEqualTo("custom.tool:name");
      assertThat(config.getTools().getExtensionPropertyInboundConnectorType())
          .isEqualTo("custom.inbound.type");
      assertThat(config.getTools().getExtensionPropertyPrefixToolProperties())
          .isEqualTo("custom.tool:");
    }
  }

  @Nested
  @ActiveProfiles("broker")
  @SpringJUnitConfig({
    UnifiedConfiguration.class,
    UnifiedConfigurationHelper.class,
    BrokerBasedPropertiesOverride.class,
  })
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
      })
  class RdbmsExporterWithDefaultTools {
    @Test
    void shouldApplyDefaultToolsConfigToRdbmsExporter(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      final var exporter = brokerBasedProperties.getRdbmsExporter();
      final var config =
          ExporterConfiguration.fromArgs(
              io.camunda.exporter.rdbms.ExporterConfiguration.class, exporter.getArgs());

      assertThat(config.getTools().getExtensionPropertyToolName())
          .isEqualTo(Tools.DEFAULT_EXTENSION_PROPERTY_TOOL_NAME);
      assertThat(config.getTools().getExtensionPropertyInboundConnectorType())
          .isEqualTo(Tools.DEFAULT_EXTENSION_PROPERTY_INBOUND_CONNECTOR_TYPE);
      assertThat(config.getTools().getExtensionPropertyPrefixToolProperties())
          .isEqualTo(Tools.DEFAULT_EXTENSION_PROPERTY_PREFIX_TOOL_PROPERTIES);
    }
  }
}
