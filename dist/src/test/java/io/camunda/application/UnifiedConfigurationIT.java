/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.ActorClockControlledPropertiesOverride;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beanoverrides.GatewayBasedPropertiesOverride;
import io.camunda.configuration.beanoverrides.IdleStrategyPropertiesOverride;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.operate.conditions.DatabaseType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    classes = {
      StandaloneCamunda.class,
      // Unified Configuration classes
      UnifiedConfiguration.class,
      UnifiedConfigurationHelper.class,
      TasklistPropertiesOverride.class,
      OperatePropertiesOverride.class,
      GatewayBasedPropertiesOverride.class,
      BrokerBasedPropertiesOverride.class,
      ActorClockControlledPropertiesOverride.class,
      IdleStrategyPropertiesOverride.class
    })
@TestPropertySource(
    properties = {
      // Using opensearch because it's different than the default value
      "camunda.data.secondary-storage.type=opensearch"
    })
@ActiveProfiles({"broker", "tasklist", "operate"})
public class UnifiedConfigurationIT {

  static final String OPENSEARCH_CONTAINER_NAME = "opensearchproject/opensearch";
  static final String OPENSEARCH_CONTAINER_VERSION = "2.13.0";
  static final int OPENSEARCH_DEFAULT_PORT = 9200;

  static GenericContainer<?> opensearchContainer;

  @Autowired UnifiedConfiguration unifiedConfiguration;
  @Autowired OperateProperties operateProperties;
  @Autowired TasklistProperties tasklistProperties;
  @Autowired BrokerBasedProperties brokerBasedProperties;

  @BeforeAll
  static void setup() {
    opensearchContainer =
        new GenericContainer<>(
                DockerImageName.parse(
                    OPENSEARCH_CONTAINER_NAME + ":" + OPENSEARCH_CONTAINER_VERSION))
            .withExposedPorts(OPENSEARCH_DEFAULT_PORT);
    opensearchContainer.start();
  }

  @AfterAll
  static void tearDown() {
    if (opensearchContainer != null) {
      opensearchContainer.stop();
    }
  }

  @DynamicPropertySource
  static void registerElasticsearchContainerProperties(DynamicPropertyRegistry registry) {
    registry.add("camunda.data.secondary-storage.opensearch.url", () -> getOpensearchEndpoint());
  }

  @Test
  public void testUnifiedConfigurationLoadsSuccessfully() {
    assertThat(unifiedConfiguration.getCamunda().getData().getSecondaryStorage().getType())
        .isEqualTo(SecondaryStorageType.opensearch);

    validateOperateProperties();
    validateTasklistProperties();
    validateCamundaExporterProperties();
  }

  void validateOperateProperties() {
    assertThat(operateProperties.getDatabase()).isEqualTo(DatabaseType.Opensearch);
    assertThat(operateProperties.getOpensearch().getUrl()).isEqualTo(getOpensearchEndpoint());
  }

  void validateTasklistProperties() {
    assertThat(tasklistProperties.getDatabase().toLowerCase()).isEqualTo("opensearch");
    assertThat(tasklistProperties.getOpenSearch().getUrl()).isEqualTo(getOpensearchEndpoint());
  }

  void validateCamundaExporterProperties() {
    final ExporterCfg camundaExporter =
        UnifiedConfigurationHelper.getCamundaExporter(brokerBasedProperties);
    assertThat(camundaExporter).isNotNull();
    final Map<String, Object> args = camundaExporter.getArgs();
    assertThat(args).isNotNull();

    final ExporterConfiguration exporterConfiguration =
        UnifiedConfigurationHelper.argsToExporterConfiguration(args);
    assertThat(exporterConfiguration.getConnect().getType().toLowerCase()).isEqualTo("opensearch");
    assertThat(exporterConfiguration.getConnect().getUrl()).isEqualTo(getOpensearchEndpoint());
  }

  private static String getOpensearchEndpoint() {
    return "http://"
        + opensearchContainer.getHost()
        + ":"
        + opensearchContainer.getMappedPort(OPENSEARCH_DEFAULT_PORT);
  }
}
