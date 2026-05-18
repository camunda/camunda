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
    final ExtensionProperties extensionProperties = new ExtensionProperties();

    assertThat(extensionProperties.getExtensionPropertyToolName())
        .isEqualTo(ExtensionProperties.DEFAULT_EXTENSION_PROPERTY_TOOL_NAME);
    assertThat(extensionProperties.getExtensionPropertyInboundConnectorType())
        .isEqualTo(ExtensionProperties.DEFAULT_EXTENSION_PROPERTY_INBOUND_CONNECTOR_TYPE);
    assertThat(extensionProperties.getExtensionPropertyPrefixToolProperties())
        .isEqualTo(ExtensionProperties.DEFAULT_EXTENSION_PROPERTY_PREFIX_TOOL_PROPERTIES);
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
        "camunda.data.extension-properties.extension-property-tool-name=custom.tool:name",
        "camunda.data.extension-properties.extension-property-inbound-connector-type=custom.inbound.type",
        "camunda.data.extension-properties.extension-property-prefix-tool-properties=custom.tool:",
      })
  class CamundaExporterWithCustomTools {
    @Test
    void shouldApplyToolsConfigToCamundaExporter(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      final var exporter = brokerBasedProperties.getCamundaExporter();
      final var config =
          ExporterConfiguration.fromArgs(
              io.camunda.exporter.config.ExporterConfiguration.class, exporter.getArgs());

      assertThat(config.getExtensionProperties().getExtensionPropertyToolName())
          .isEqualTo("custom.tool:name");
      assertThat(config.getExtensionProperties().getExtensionPropertyInboundConnectorType())
          .isEqualTo("custom.inbound.type");
      assertThat(config.getExtensionProperties().getExtensionPropertyPrefixToolProperties())
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

      assertThat(config.getExtensionProperties().getExtensionPropertyToolName())
          .isEqualTo(ExtensionProperties.DEFAULT_EXTENSION_PROPERTY_TOOL_NAME);
      assertThat(config.getExtensionProperties().getExtensionPropertyInboundConnectorType())
          .isEqualTo(ExtensionProperties.DEFAULT_EXTENSION_PROPERTY_INBOUND_CONNECTOR_TYPE);
      assertThat(config.getExtensionProperties().getExtensionPropertyPrefixToolProperties())
          .isEqualTo(ExtensionProperties.DEFAULT_EXTENSION_PROPERTY_PREFIX_TOOL_PROPERTIES);
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
        "camunda.data.extension-properties.extension-property-tool-name=custom.tool:name",
        "camunda.data.extension-properties.extension-property-inbound-connector-type=custom.inbound.type",
        "camunda.data.extension-properties.extension-property-prefix-tool-properties=custom.tool:",
      })
  class RdbmsExporterWithCustomTools {
    @Test
    void shouldApplyToolsConfigToRdbmsExporter(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      final var exporter = brokerBasedProperties.getRdbmsExporter();
      final var config =
          ExporterConfiguration.fromArgs(
              io.camunda.exporter.rdbms.ExporterConfiguration.class, exporter.getArgs());

      assertThat(config.getExtensionProperties().getExtensionPropertyToolName())
          .isEqualTo("custom.tool:name");
      assertThat(config.getExtensionProperties().getExtensionPropertyInboundConnectorType())
          .isEqualTo("custom.inbound.type");
      assertThat(config.getExtensionProperties().getExtensionPropertyPrefixToolProperties())
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

      assertThat(config.getExtensionProperties().getExtensionPropertyToolName())
          .isEqualTo(ExtensionProperties.DEFAULT_EXTENSION_PROPERTY_TOOL_NAME);
      assertThat(config.getExtensionProperties().getExtensionPropertyInboundConnectorType())
          .isEqualTo(ExtensionProperties.DEFAULT_EXTENSION_PROPERTY_INBOUND_CONNECTOR_TYPE);
      assertThat(config.getExtensionProperties().getExtensionPropertyPrefixToolProperties())
          .isEqualTo(ExtensionProperties.DEFAULT_EXTENSION_PROPERTY_PREFIX_TOOL_PROPERTIES);
    }
  }
}
