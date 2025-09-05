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
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.operate.property.OperateProperties;
import io.camunda.search.connect.configuration.SecurityConfiguration;
import io.camunda.tasklist.property.SslProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@ActiveProfiles({"broker"})
@SpringJUnitConfig({
  UnifiedConfiguration.class,
  UnifiedConfigurationHelper.class,
  SearchEngineConnectPropertiesOverride.class,
  BrokerBasedPropertiesOverride.class,
  TasklistPropertiesOverride.class,
  OperatePropertiesOverride.class,
})
public class SecurityElasticsearchTest {

  private ExporterConfiguration getExporterConfiguration(
      final BrokerBasedProperties brokerBasedProperties) {

    final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
    assertThat(camundaExporter).isNotNull();

    final Map<String, Object> args = camundaExporter.getArgs();
    assertThat(args).isNotNull();

    return UnifiedConfigurationHelper.argsToExporterConfiguration(args);
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        "camunda.data.secondary-storage.elasticsearch.security.enabled=true",
        "camunda.data.secondary-storage.elasticsearch.security.certificate-path=certificatePath",
        "camunda.data.secondary-storage.elasticsearch.security.verify-hostname=false",
        "camunda.data.secondary-storage.elasticsearch.security.self-signed=true"
      })
  class WithOnlyUnifiedConfigSet {
    final SearchEngineConnectProperties searchEngineConnectProperties;
    final BrokerBasedProperties brokerBasedProperties;
    final TasklistProperties tasklistProperties;
    final OperateProperties operateProperties;

    WithOnlyUnifiedConfigSet(
        @Autowired final SearchEngineConnectProperties searchEngineConnectProperties,
        @Autowired final BrokerBasedProperties brokerBasedProperties,
        @Autowired final TasklistProperties tasklistProperties,
        @Autowired final OperateProperties operateProperties) {
      this.searchEngineConnectProperties = searchEngineConnectProperties;
      this.brokerBasedProperties = brokerBasedProperties;
      this.tasklistProperties = tasklistProperties;
      this.operateProperties = operateProperties;
    }

    @Test
    void testCamundaSearchEngineConnectProperties() {
      assertThat(searchEngineConnectProperties.getSecurity())
          .returns(true, SecurityConfiguration::isEnabled)
          .returns("certificatePath", SecurityConfiguration::getCertificatePath)
          .returns(false, SecurityConfiguration::isVerifyHostname)
          .returns(true, SecurityConfiguration::isSelfSigned);
    }

    @Test
    void testCamundaExporterProperties() {
      final ExporterConfiguration exporterConfiguration =
          getExporterConfiguration(brokerBasedProperties);

      assertThat(exporterConfiguration.getConnect().getSecurity())
          .returns(true, SecurityConfiguration::isEnabled)
          .returns("certificatePath", SecurityConfiguration::getCertificatePath)
          .returns(false, SecurityConfiguration::isVerifyHostname)
          .returns(true, SecurityConfiguration::isSelfSigned);
    }

    @Test
    void testCamundaTasklistProperties() {
      assertThat(tasklistProperties.getElasticsearch().getSsl())
          .returns("certificatePath", SslProperties::getCertificatePath)
          .returns(false, SslProperties::isVerifyHostname)
          .returns(true, SslProperties::isSelfSigned);
    }

    @Test
    void testCamundaOperateProperties() {
      assertThat(operateProperties.getElasticsearch().getSsl())
          .returns("certificatePath", io.camunda.operate.property.SslProperties::getCertificatePath)
          .returns(false, io.camunda.operate.property.SslProperties::isVerifyHostname)
          .returns(true, io.camunda.operate.property.SslProperties::isSelfSigned);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        // enabled
        "camunda.data.secondary-storage.elasticsearch.security.enabled=true",
        "camunda.database.security.enabled=true",
        // certificate path
        "camunda.data.secondary-storage.elasticsearch.security.certificate-path=certificatePath",
        "camunda.database.security.certificatePath=certificatePath",
        "camunda.tasklist.elasticsearch.ssl.certificatePath=certificatePath",
        "camunda.operate.elasticsearch.ssl.certificatePath=certificatePath",
        // verify hostname
        "camunda.data.secondary-storage.elasticsearch.security.verify-hostname=false",
        "camunda.database.security.verifyHostname=false",
        "camunda.tasklist.elasticsearch.ssl.verifyHostname=false",
        "camunda.operate.elasticsearch.ssl.verifyHostname=false",
        // self signed
        "camunda.data.secondary-storage.elasticsearch.security.self-signed=true",
        "camunda.database.security.selfSigned=true",
        "camunda.tasklist.elasticsearch.ssl.selfSigned=true",
        "camunda.operate.elasticsearch.ssl.selfSigned=true"
      })
  class WithNewAndLegacySet {
    final SearchEngineConnectProperties searchEngineConnectProperties;
    final BrokerBasedProperties brokerBasedProperties;
    final TasklistProperties tasklistProperties;
    final OperateProperties operateProperties;

    WithNewAndLegacySet(
        @Autowired final SearchEngineConnectProperties searchEngineConnectProperties,
        @Autowired final BrokerBasedProperties brokerBasedProperties,
        @Autowired final TasklistProperties tasklistProperties,
        @Autowired final OperateProperties operateProperties) {
      this.searchEngineConnectProperties = searchEngineConnectProperties;
      this.brokerBasedProperties = brokerBasedProperties;
      this.tasklistProperties = tasklistProperties;
      this.operateProperties = operateProperties;
    }

    @Test
    void testCamundaSearchEngineConnectProperties() {
      assertThat(searchEngineConnectProperties.getSecurity())
          .returns(true, SecurityConfiguration::isEnabled)
          .returns("certificatePath", SecurityConfiguration::getCertificatePath)
          .returns(false, SecurityConfiguration::isVerifyHostname)
          .returns(true, SecurityConfiguration::isSelfSigned);
    }

    @Test
    void testCamundaExporterProperties() {
      final ExporterConfiguration exporterConfiguration =
          getExporterConfiguration(brokerBasedProperties);

      assertThat(exporterConfiguration.getConnect().getSecurity())
          .returns(true, SecurityConfiguration::isEnabled)
          .returns("certificatePath", SecurityConfiguration::getCertificatePath)
          .returns(false, SecurityConfiguration::isVerifyHostname)
          .returns(true, SecurityConfiguration::isSelfSigned);
    }

    @Test
    void testCamundaTasklistProperties() {
      assertThat(tasklistProperties.getElasticsearch().getSsl())
          .returns("certificatePath", SslProperties::getCertificatePath)
          .returns(false, SslProperties::isVerifyHostname)
          .returns(true, SslProperties::isSelfSigned);
    }

    @Test
    void testCamundaOperateProperties() {
      assertThat(operateProperties.getElasticsearch().getSsl())
          .returns("certificatePath", io.camunda.operate.property.SslProperties::getCertificatePath)
          .returns(false, io.camunda.operate.property.SslProperties::isVerifyHostname)
          .returns(true, io.camunda.operate.property.SslProperties::isSelfSigned);
    }
  }
}
