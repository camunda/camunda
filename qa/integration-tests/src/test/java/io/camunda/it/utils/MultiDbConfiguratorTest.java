/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.utils;

import static io.camunda.it.utils.MultiDbConfigurator.zeebePrefix;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.configuration.BrokerBasedConfiguration.BrokerBasedProperties;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MultiDbConfiguratorTest {

  public static final String EXPECTED_PREFIX = "custom";
  public static final String EXPECTED_ZEEBE_PREFIX = "custom" + zeebePrefix;
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

    /* Tasklist Config Assertions */
    assertProperty(
        testSimpleCamundaApplication,
        "camunda.tasklist.elasticsearch.indexPrefix",
        EXPECTED_PREFIX);
    assertProperty(
        testSimpleCamundaApplication, "camunda.tasklist.elasticsearch.url", EXPECTED_URL);
    assertProperty(
        testSimpleCamundaApplication, "camunda.tasklist.zeebeElasticsearch.url", EXPECTED_URL);
    assertProperty(
        testSimpleCamundaApplication,
        "camunda.tasklist.elasticsearch.indexPrefix",
        EXPECTED_PREFIX);
    assertProperty(
        testSimpleCamundaApplication,
        "camunda.tasklist.zeebeElasticsearch.prefix",
        EXPECTED_ZEEBE_PREFIX);

    /* Operate Config Assertions */
    assertProperty(
        testSimpleCamundaApplication, "camunda.operate.elasticsearch.indexPrefix", EXPECTED_PREFIX);
    assertProperty(testSimpleCamundaApplication, "camunda.operate.elasticsearch.url", EXPECTED_URL);
    assertProperty(
        testSimpleCamundaApplication, "camunda.operate.zeebeElasticsearch.url", EXPECTED_URL);
    assertProperty(
        testSimpleCamundaApplication, "camunda.operate.elasticsearch.indexPrefix", EXPECTED_PREFIX);
    assertProperty(
        testSimpleCamundaApplication,
        "camunda.operate.zeebeElasticsearch.prefix",
        EXPECTED_ZEEBE_PREFIX);

    /* Camunda Config Assertions */

    assertProperty(testSimpleCamundaApplication, "camunda.database.indexPrefix", EXPECTED_PREFIX);
    assertProperty(testSimpleCamundaApplication, "camunda.database.url", EXPECTED_URL);

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

    /* Tasklist Config Assertions */
    assertProperty(
        testSimpleCamundaApplication, "camunda.tasklist.opensearch.indexPrefix", EXPECTED_PREFIX);
    assertProperty(testSimpleCamundaApplication, "camunda.tasklist.opensearch.url", EXPECTED_URL);
    assertProperty(
        testSimpleCamundaApplication, "camunda.tasklist.zeebeOpensearch.url", EXPECTED_URL);
    assertProperty(
        testSimpleCamundaApplication, "camunda.tasklist.opensearch.indexPrefix", EXPECTED_PREFIX);
    assertProperty(
        testSimpleCamundaApplication,
        "camunda.tasklist.zeebeOpensearch.prefix",
        EXPECTED_ZEEBE_PREFIX);
    assertProperty(
        testSimpleCamundaApplication, "camunda.tasklist.opensearch.username", EXPECTED_USER);
    assertProperty(
        testSimpleCamundaApplication, "camunda.tasklist.opensearch.password", EXPECTED_PW);

    /* Operate Config Assertions */
    assertProperty(
        testSimpleCamundaApplication, "camunda.operate.opensearch.indexPrefix", EXPECTED_PREFIX);
    assertProperty(testSimpleCamundaApplication, "camunda.operate.opensearch.url", EXPECTED_URL);
    assertProperty(
        testSimpleCamundaApplication, "camunda.operate.zeebeOpensearch.url", EXPECTED_URL);
    assertProperty(
        testSimpleCamundaApplication, "camunda.operate.opensearch.indexPrefix", EXPECTED_PREFIX);
    assertProperty(
        testSimpleCamundaApplication,
        "camunda.operate.zeebeOpensearch.prefix",
        EXPECTED_ZEEBE_PREFIX);
    assertProperty(
        testSimpleCamundaApplication, "camunda.operate.opensearch.username", EXPECTED_USER);
    assertProperty(
        testSimpleCamundaApplication, "camunda.operate.opensearch.password", EXPECTED_PW);

    /* Camunda Config Assertions */

    assertProperty(testSimpleCamundaApplication, "camunda.database.indexPrefix", EXPECTED_PREFIX);
    assertProperty(testSimpleCamundaApplication, "camunda.database.url", EXPECTED_URL);
    assertProperty(testSimpleCamundaApplication, "camunda.database.username", EXPECTED_USER);
    assertProperty(testSimpleCamundaApplication, "camunda.database.password", EXPECTED_PW);

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

  private <T> void assertProperty(
      final TestSimpleCamundaApplication applicationContext,
      final String propertyKey,
      final T expectedValue) {
    final T propertyValue =
        applicationContext.property(propertyKey, (Class<T>) expectedValue.getClass(), null);
    assertThat(propertyValue).isEqualTo(expectedValue);
  }
}
