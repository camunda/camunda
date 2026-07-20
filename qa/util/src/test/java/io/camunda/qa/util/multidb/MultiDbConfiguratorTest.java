/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import static io.camunda.qa.util.multidb.MultiDbConfigurator.zeebePrefix;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.DocumentBasedSecondaryStorageDatabase;
import io.camunda.configuration.Exporter;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporter;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MultiDbConfiguratorTest {

  public static final String EXPECTED_PREFIX = "custom";
  public static final String EXPECTED_ZEEBE_PREFIX = "custom-" + zeebePrefix;
  public static final String EXPECTED_URL = "localhost";
  public static final String EXPECTED_USER = "user";
  public static final String EXPECTED_PW = "pw";

  @Test
  public void shouldConfigureWithElasticsearch() {
    // given
    final var testSimpleCamundaApplication = new TestCamundaApplication();
    final MultiDbConfigurator multiDbConfigurator =
        new MultiDbConfigurator(testSimpleCamundaApplication);

    // when
    multiDbConfigurator.configureElasticsearchSupport(EXPECTED_URL, EXPECTED_PREFIX);

    // then
    final SecondaryStorage secondaryStorage =
        testSimpleCamundaApplication.unifiedConfig().getData().getSecondaryStorage();
    assertThat(secondaryStorage.getType()).isEqualTo(SecondaryStorageType.elasticsearch);
    assertThat(secondaryStorage.getRetention().isEnabled()).isFalse();
    assertThat(secondaryStorage.getRetention().getMinimumAge()).isEqualTo("0s");
    assertDocumentBasedDatabase(
        secondaryStorage.getElasticsearch(), EXPECTED_URL, EXPECTED_PREFIX, "1h");

    // the camunda exporter is not declared explicitly; it is derived from the unified
    // secondary-storage configuration at startup so that physical tenants export to their own
    // storage instead of inheriting a root-pinned connection
    assertNoExplicitCamundaExporter(testSimpleCamundaApplication);
  }

  @Test
  public void shouldConfigureElasticsearchWithRetention() {
    // given
    final var testSimpleCamundaApplication = new TestCamundaApplication();
    final MultiDbConfigurator multiDbConfigurator =
        new MultiDbConfigurator(testSimpleCamundaApplication);

    // when
    multiDbConfigurator.configureElasticsearchSupport(EXPECTED_URL, EXPECTED_PREFIX, true);

    // then
    final SecondaryStorage secondaryStorage =
        testSimpleCamundaApplication.unifiedConfig().getData().getSecondaryStorage();
    assertThat(secondaryStorage.getRetention().isEnabled()).isTrue();
    assertThat(secondaryStorage.getRetention().getMinimumAge()).isEqualTo("0s");
    assertDocumentBasedDatabase(
        secondaryStorage.getElasticsearch(), EXPECTED_URL, EXPECTED_PREFIX, "1s");
    assertNoExplicitCamundaExporter(testSimpleCamundaApplication);
  }

  @Test
  public void shouldConfigureWithOldElasticsearchExporter() {
    // given
    final var testSimpleCamundaApplication = new TestCamundaApplication();
    final MultiDbConfigurator multiDbConfigurator =
        new MultiDbConfigurator(testSimpleCamundaApplication);

    // when
    multiDbConfigurator.configureElasticsearchSupportIncludingOldExporter(
        EXPECTED_URL, EXPECTED_PREFIX);

    // then
    final SecondaryStorage secondaryStorage =
        testSimpleCamundaApplication.unifiedConfig().getData().getSecondaryStorage();
    assertThat(secondaryStorage.getType()).isEqualTo(SecondaryStorageType.elasticsearch);
    assertDocumentBasedDatabase(
        secondaryStorage.getElasticsearch(), EXPECTED_URL, EXPECTED_PREFIX, "1h");

    final Exporter esExporter =
        testSimpleCamundaApplication
            .unifiedConfig()
            .getData()
            .getExporters()
            .get(ElasticsearchExporter.class.getSimpleName().toLowerCase());
    assertThat(esExporter).isNotNull();
    assertThat(esExporter.getClassName()).isEqualTo(ElasticsearchExporter.class.getName());
    assertThat(esExporter.getArgs())
        .isEqualTo(
            Map.of(
                "url",
                EXPECTED_URL,
                "index",
                Map.of("prefix", EXPECTED_ZEEBE_PREFIX),
                "bulk",
                Map.of("size", 1)));

    assertNoExplicitCamundaExporter(testSimpleCamundaApplication);
  }

  @Test
  public void shouldConfigureWithOpensearch() {
    // given
    final var testSimpleCamundaApplication = new TestCamundaApplication();
    final MultiDbConfigurator multiDbConfigurator =
        new MultiDbConfigurator(testSimpleCamundaApplication);

    // when
    multiDbConfigurator.configureOpenSearchSupport(
        EXPECTED_URL, EXPECTED_PREFIX, EXPECTED_USER, EXPECTED_PW);

    // then
    final SecondaryStorage secondaryStorage =
        testSimpleCamundaApplication.unifiedConfig().getData().getSecondaryStorage();
    assertThat(secondaryStorage.getType()).isEqualTo(SecondaryStorageType.opensearch);
    assertThat(secondaryStorage.getRetention().isEnabled()).isFalse();
    assertDocumentBasedDatabase(
        secondaryStorage.getOpensearch(), EXPECTED_URL, EXPECTED_PREFIX, "1h");
    assertThat(secondaryStorage.getOpensearch().getUsername()).isEqualTo(EXPECTED_USER);
    assertThat(secondaryStorage.getOpensearch().getPassword()).isEqualTo(EXPECTED_PW);
    assertThat(secondaryStorage.getOpensearch().isAwsEnabled()).isFalse();

    assertNoExplicitCamundaExporter(testSimpleCamundaApplication);
  }

  @Test
  public void shouldConfigureWithOpenSearchWithRetention() {
    // given
    final var testSimpleCamundaApplication = new TestCamundaApplication();
    final MultiDbConfigurator multiDbConfigurator =
        new MultiDbConfigurator(testSimpleCamundaApplication);

    // when
    multiDbConfigurator.configureOpenSearchSupport(
        EXPECTED_URL, EXPECTED_PREFIX, EXPECTED_USER, EXPECTED_PW, true, false);

    // then
    final SecondaryStorage secondaryStorage =
        testSimpleCamundaApplication.unifiedConfig().getData().getSecondaryStorage();
    assertThat(secondaryStorage.getRetention().isEnabled()).isTrue();
    assertThat(secondaryStorage.getRetention().getMinimumAge()).isEqualTo("0s");
    assertDocumentBasedDatabase(
        secondaryStorage.getOpensearch(), EXPECTED_URL, EXPECTED_PREFIX, "1s");
    assertNoExplicitCamundaExporter(testSimpleCamundaApplication);
  }

  @Test
  public void shouldConfigureWithOpensearchIncludingOldExporter() {
    // given
    final var testSimpleCamundaApplication = new TestCamundaApplication();
    final MultiDbConfigurator multiDbConfigurator =
        new MultiDbConfigurator(testSimpleCamundaApplication);

    // when
    multiDbConfigurator.configureOpenSearchSupportIncludingOldExporter(
        EXPECTED_URL, EXPECTED_PREFIX, EXPECTED_USER, EXPECTED_PW);

    // then
    final SecondaryStorage secondaryStorage =
        testSimpleCamundaApplication.unifiedConfig().getData().getSecondaryStorage();
    assertThat(secondaryStorage.getType()).isEqualTo(SecondaryStorageType.opensearch);
    assertDocumentBasedDatabase(
        secondaryStorage.getOpensearch(), EXPECTED_URL, EXPECTED_PREFIX, "1h");

    final Exporter osExporter =
        testSimpleCamundaApplication
            .unifiedConfig()
            .getData()
            .getExporters()
            .get(OpensearchExporter.class.getSimpleName().toLowerCase());
    assertThat(osExporter).isNotNull();
    assertThat(osExporter.getClassName()).isEqualTo(OpensearchExporter.class.getName());
    assertThat(osExporter.getArgs())
        .isEqualTo(
            Map.of(
                "url",
                EXPECTED_URL,
                "index",
                Map.of("prefix", EXPECTED_ZEEBE_PREFIX),
                "bulk",
                Map.of("size", 1),
                "authentication",
                Map.of("username", EXPECTED_USER, "password", EXPECTED_PW)));

    assertNoExplicitCamundaExporter(testSimpleCamundaApplication);
  }

  private static void assertDocumentBasedDatabase(
      final DocumentBasedSecondaryStorageDatabase database,
      final String expectedUrl,
      final String expectedPrefix,
      final String expectedWaitPeriodBeforeArchiving) {
    assertThat(database.getUrl()).isEqualTo(expectedUrl);
    assertThat(database.getIndexPrefix()).isEqualTo(expectedPrefix);
    assertThat(database.getHistory().getPolicyName()).isEqualTo(expectedPrefix + "-ilm");
    assertThat(database.getHistory().getWaitPeriodBeforeArchiving())
        .isEqualTo(expectedWaitPeriodBeforeArchiving);
    assertThat(database.getBulk().getSize()).isEqualTo(1);
    assertThat(database.getRefreshInterval()).isEqualTo("100ms");
    assertThat(database.getHistory().getDelayBetweenRuns()).isEqualTo(Duration.ofMillis(1000));
    assertThat(database.getHistory().getMaxDelayBetweenRuns()).isEqualTo(Duration.ofMillis(1000));
    assertThat(database.isCreateSchema()).isTrue();
  }

  private static void assertNoExplicitCamundaExporter(
      final TestCamundaApplication testApplication) {
    assertThat(testApplication.unifiedConfig().getData().getExporters())
        .doesNotContainKey("camundaexporter");
  }

  @SuppressWarnings("unchecked")
  private <T> void assertProperty(
      final TestCamundaApplication applicationContext,
      final String propertyKey,
      final T expectedValue) {
    final T propertyValue =
        applicationContext.property(propertyKey, (Class<T>) expectedValue.getClass(), null);
    assertThat(propertyValue).isEqualTo(expectedValue);
  }
}
