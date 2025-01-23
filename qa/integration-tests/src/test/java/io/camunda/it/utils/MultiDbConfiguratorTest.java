/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.configuration.BrokerBasedConfiguration.BrokerBasedProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MultiDbConfiguratorTest {

  public static final String EXPECTED_PREFIX = "custom";
  public static final String EXPECTED_URL = "localhost";
  public static final String EXPECTED_USER = "user";
  public static final String EXPECTED_PW = "pw";

  @Test
  public void shouldRegisterOperateAndTasklistPropertiesByDefault() {
    // given
    final var testSimpleCamundaApplication = new TestSimpleCamundaApplication();

    // when
    new MultiDbConfigurator(testSimpleCamundaApplication);

    // then
    assertThat(testSimpleCamundaApplication.bean(OperateProperties.class)).isNotNull();
    assertThat(testSimpleCamundaApplication.bean(TasklistProperties.class)).isNotNull();

    final BrokerBasedProperties brokerBasedProperties =
        testSimpleCamundaApplication.bean(BrokerBasedProperties.class);
    assertThat(brokerBasedProperties.getExporters()).isEmpty();
  }

  @Test
  public void shouldConfigureWithElasticsearch() {
    // given
    final var testSimpleCamundaApplication = new TestSimpleCamundaApplication();
    final MultiDbConfigurator multiDbConfigurator =
        new MultiDbConfigurator(testSimpleCamundaApplication);

    // when
    multiDbConfigurator.configureElasticsearchSupport(EXPECTED_URL, EXPECTED_PREFIX);

    // then
    final String configuredPrefix =
        testSimpleCamundaApplication.property("camunda.database.indexPrefix", String.class, "");
    assertThat(configuredPrefix).isEqualTo(EXPECTED_PREFIX);
    final String configuredUrl =
        testSimpleCamundaApplication.property("camunda.database.url", String.class, "");
    assertThat(configuredUrl).isEqualTo(EXPECTED_URL);

    final OperateProperties operateProperties =
        testSimpleCamundaApplication.bean(OperateProperties.class);
    assertThat(operateProperties).isNotNull();

    assertThat(operateProperties.getElasticsearch().getIndexPrefix()).isEqualTo(EXPECTED_PREFIX);
    assertThat(operateProperties.getElasticsearch().getUrl()).isEqualTo(EXPECTED_URL);

    final TasklistProperties tasklistProperties =
        testSimpleCamundaApplication.bean(TasklistProperties.class);
    assertThat(tasklistProperties).isNotNull();
    assertThat(tasklistProperties.getElasticsearch().getIndexPrefix()).isEqualTo(EXPECTED_PREFIX);
    assertThat(tasklistProperties.getElasticsearch().getUrl()).isEqualTo(EXPECTED_URL);

    final BrokerBasedProperties brokerBasedProperties =
        testSimpleCamundaApplication.bean(BrokerBasedProperties.class);
    assertThat(brokerBasedProperties).isNotNull();

    final ExporterCfg camundaExporter = brokerBasedProperties.getExporters().get("CamundaExporter");
    assertThat(camundaExporter).isNotNull();

    final Map<String, Object> exporterArgs = camundaExporter.getArgs();
    assertThat(exporterArgs.get("index")).isEqualTo(Map.of("prefix", EXPECTED_PREFIX));

    assertThat(exporterArgs.get("connect"))
        .isEqualTo(
            Map.of(
                "url",
                EXPECTED_URL,
                "indexPrefix",
                EXPECTED_PREFIX,
                "type",
                io.camunda.search.connect.configuration.DatabaseType.ELASTICSEARCH));
  }

  @Test
  public void shouldConfigureWithOpensearch() {
    // given
    final var testSimpleCamundaApplication = new TestSimpleCamundaApplication();
    final MultiDbConfigurator multiDbConfigurator =
        new MultiDbConfigurator(testSimpleCamundaApplication);

    // when
    multiDbConfigurator.configureOpenSearchSupport(
        EXPECTED_URL, EXPECTED_PREFIX, EXPECTED_USER, EXPECTED_PW);

    // then
    final String configuredPrefix =
        testSimpleCamundaApplication.property("camunda.database.indexPrefix", String.class, "");
    assertThat(configuredPrefix).isEqualTo(EXPECTED_PREFIX);
    final String configuredUser =
        testSimpleCamundaApplication.property("camunda.database.username", String.class, "");
    assertThat(configuredUser).isEqualTo(EXPECTED_USER);
    final String configuredPassword =
        testSimpleCamundaApplication.property("camunda.database.password", String.class, "");
    assertThat(configuredPassword).isEqualTo(EXPECTED_PW);
    final String configuredUrl =
        testSimpleCamundaApplication.property("camunda.database.url", String.class, "");
    assertThat(configuredUrl).isEqualTo(EXPECTED_URL);

    final OperateProperties operateProperties =
        testSimpleCamundaApplication.bean(OperateProperties.class);
    assertThat(operateProperties).isNotNull();

    assertThat(operateProperties.getOpensearch().getIndexPrefix()).isEqualTo(EXPECTED_PREFIX);
    assertThat(operateProperties.getOpensearch().getUrl()).isEqualTo(EXPECTED_URL);
    assertThat(operateProperties.getOpensearch().getPassword()).isEqualTo(EXPECTED_PW);
    assertThat(operateProperties.getOpensearch().getUsername()).isEqualTo(EXPECTED_USER);

    final TasklistProperties tasklistProperties =
        testSimpleCamundaApplication.bean(TasklistProperties.class);
    assertThat(tasklistProperties).isNotNull();
    assertThat(tasklistProperties.getOpenSearch().getIndexPrefix()).isEqualTo(EXPECTED_PREFIX);
    assertThat(tasklistProperties.getOpenSearch().getUrl()).isEqualTo(EXPECTED_URL);
    assertThat(tasklistProperties.getOpenSearch().getPassword()).isEqualTo(EXPECTED_PW);
    assertThat(tasklistProperties.getOpenSearch().getUsername()).isEqualTo(EXPECTED_USER);

    final BrokerBasedProperties brokerBasedProperties =
        testSimpleCamundaApplication.bean(BrokerBasedProperties.class);
    assertThat(brokerBasedProperties).isNotNull();

    final ExporterCfg camundaExporter = brokerBasedProperties.getExporters().get("CamundaExporter");
    assertThat(camundaExporter).isNotNull();

    final Map<String, Object> exporterArgs = camundaExporter.getArgs();
    assertThat(exporterArgs.get("index")).isEqualTo(Map.of("prefix", EXPECTED_PREFIX));

    assertThat(exporterArgs.get("connect"))
        .isEqualTo(
            Map.of(
                "url",
                EXPECTED_URL,
                "indexPrefix",
                EXPECTED_PREFIX,
                "type",
                io.camunda.search.connect.configuration.DatabaseType.OPENSEARCH,
                "username",
                EXPECTED_USER,
                "password",
                EXPECTED_PW));
  }
}
