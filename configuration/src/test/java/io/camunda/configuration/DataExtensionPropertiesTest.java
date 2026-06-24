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

class DataExtensionPropertiesTest {

  @Test
  void shouldHaveDefaults() {
    final ExtensionProperties extensionProperties = new ExtensionProperties();

    assertThat(extensionProperties.getToolNameProperty())
        .isEqualTo(ExtensionProperties.DEFAULT_TOOL_NAME_PROPERTY);
    assertThat(extensionProperties.getInboundConnectorTypeProperty())
        .isEqualTo(ExtensionProperties.DEFAULT_INBOUND_CONNECTOR_TYPE_PROPERTY);
    assertThat(extensionProperties.getToolPropertiesPrefix())
        .isEqualTo(ExtensionProperties.DEFAULT_TOOL_PROPERTIES_PREFIX);
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
        "camunda.data.extension-properties.tool-name-property=custom.tool:name",
        "camunda.data.extension-properties.inbound-connector-type-property=custom.inbound.type",
        "camunda.data.extension-properties.tool-properties-prefix=custom.tool:",
      })
  class CamundaExporterWithCustomExtensionProperties {
    @Test
    void shouldApplyExtensionPropertiesConfigToCamundaExporter(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      final var exporter = brokerBasedProperties.getCamundaExporter();
      final var config =
          ExporterConfiguration.fromArgs(
              io.camunda.exporter.config.ExporterConfiguration.class, exporter.getArgs());

      assertThat(config.getExtensionProperties().getToolNameProperty())
          .isEqualTo("custom.tool:name");
      assertThat(config.getExtensionProperties().getInboundConnectorTypeProperty())
          .isEqualTo("custom.inbound.type");
      assertThat(config.getExtensionProperties().getToolPropertiesPrefix())
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
  class CamundaExporterWithDefaultExtensionProperties {
    @Test
    void shouldApplyDefaultExtensionPropertiesConfigToCamundaExporter(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      final var exporter = brokerBasedProperties.getCamundaExporter();
      final var config =
          ExporterConfiguration.fromArgs(
              io.camunda.exporter.config.ExporterConfiguration.class, exporter.getArgs());

      assertThat(config.getExtensionProperties().getToolNameProperty())
          .isEqualTo(ExtensionProperties.DEFAULT_TOOL_NAME_PROPERTY);
      assertThat(config.getExtensionProperties().getInboundConnectorTypeProperty())
          .isEqualTo(ExtensionProperties.DEFAULT_INBOUND_CONNECTOR_TYPE_PROPERTY);
      assertThat(config.getExtensionProperties().getToolPropertiesPrefix())
          .isEqualTo(ExtensionProperties.DEFAULT_TOOL_PROPERTIES_PREFIX);
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
        "camunda.data.extension-properties.tool-name-property=custom.tool:name",
        "camunda.data.extension-properties.inbound-connector-type-property=custom.inbound.type",
        "camunda.data.extension-properties.tool-properties-prefix=custom.tool:",
      })
  class RdbmsExporterWithCustomExtensionProperties {
    @Test
    void shouldApplyExtensionPropertiesConfigToRdbmsExporter(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      final var exporter = brokerBasedProperties.getRdbmsExporter();
      final var config =
          ExporterConfiguration.fromArgs(
              io.camunda.exporter.rdbms.ExporterConfiguration.class, exporter.getArgs());

      assertThat(config.getExtensionProperties().getToolNameProperty())
          .isEqualTo("custom.tool:name");
      assertThat(config.getExtensionProperties().getInboundConnectorTypeProperty())
          .isEqualTo("custom.inbound.type");
      assertThat(config.getExtensionProperties().getToolPropertiesPrefix())
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
  class RdbmsExporterWithDefaultExtensionProperties {
    @Test
    void shouldApplyDefaultExtensionPropertiesConfigToRdbmsExporter(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      final var exporter = brokerBasedProperties.getRdbmsExporter();
      final var config =
          ExporterConfiguration.fromArgs(
              io.camunda.exporter.rdbms.ExporterConfiguration.class, exporter.getArgs());

      assertThat(config.getExtensionProperties().getToolNameProperty())
          .isEqualTo(ExtensionProperties.DEFAULT_TOOL_NAME_PROPERTY);
      assertThat(config.getExtensionProperties().getInboundConnectorTypeProperty())
          .isEqualTo(ExtensionProperties.DEFAULT_INBOUND_CONNECTOR_TYPE_PROPERTY);
      assertThat(config.getExtensionProperties().getToolPropertiesPrefix())
          .isEqualTo(ExtensionProperties.DEFAULT_TOOL_PROPERTIES_PREFIX);
    }
  }
}
