/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.currentMultiDbDatabaseType;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineConnectProperties;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.statistics.response.UsageMetricsStatistics;
import io.camunda.client.impl.statistics.response.UsageMetricsStatisticsImpl;
import io.camunda.client.impl.statistics.response.UsageMetricsStatisticsItemImpl;
import io.camunda.it.util.TestHelper;
import io.camunda.operate.store.MetricsStore;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.entities.MetricEntity;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestUsageMetricMigrationApp;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * How to run this test locally:
 *
 * <ul>
 *   <li>Start a local ES/OS instance on port 9200
 *   <li>Run the test with {@code -D test.integration.camunda.database.type=es} or
 *   <li>Change the {@link
 *       io.camunda.qa.util.multidb.CamundaMultiDBExtension#currentMultiDbDatabaseType()} to always
 *       return {@link DatabaseType#ES}
 *   <li>Make sure to not commit the changes when you're done
 * </ul>
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class UsageMetricMigrationIT {

  public static final OffsetDateTime NOW = OffsetDateTime.now();
  public static final Duration EXPORT_INTERVAL = Duration.ofSeconds(2);
  public static final String TENANT_DEFAULT = "<default>";

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthenticatedAccess()
          .withAuthorizationsDisabled()
          .withBrokerConfig(
              brokerBasedProperties ->
                  brokerBasedProperties
                      .getExperimental()
                      .getEngine()
                      .getUsageMetrics()
                      .setExportInterval(EXPORT_INTERVAL));

  private static final String PROCESS_ID = "service_tasks_v1";
  private static CamundaClient camundaClient;
  private static SearchEngineConnectProperties connectConfiguration;
  private static boolean isElasticsearch;

  private static void waitForUsageMetrics(
      final CamundaClient camundaClient,
      final String tenantId,
      final Consumer<UsageMetricsStatistics> fnRequirements) {
    Awaitility.await("should export metrics to secondary storage")
        .atMost(EXPORT_INTERVAL.multipliedBy(2))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newUsageMetricsRequest(NOW.minusDays(10), NOW.plusDays(1))
                            .tenantId(tenantId)
                            .send()
                            .join())
                    .satisfies(fnRequirements));
  }

  private static void startUsageMetricMigration() {
    try (final var app = new TestUsageMetricMigrationApp(connectConfiguration)) {
      app.start();
    }
  }

  @BeforeAll
  static void setup() {
    TestHelper.deployResource(camundaClient, "process/service_tasks_v1.bpmn");
    TestHelper.startProcessInstance(camundaClient, PROCESS_ID);
    TestHelper.startProcessInstance(camundaClient, PROCESS_ID);
    TestHelper.startProcessInstance(camundaClient, PROCESS_ID);
    TestHelper.waitForProcessInstancesToStart(camundaClient, 3);
    waitForUsageMetrics(
        camundaClient, TENANT_DEFAULT, f -> assertThat(f.getProcessInstances()).isEqualTo(3));

    // generate older operate rPI metrics
    connectConfiguration = new SearchEngineConnectProperties();
    connectConfiguration.setIndexPrefix(UsageMetricMigrationIT.class.getSimpleName().toLowerCase());
    isElasticsearch = currentMultiDbDatabaseType() == DatabaseType.ES;
    if (isElasticsearch) {
      connectConfiguration.setType("elasticsearch");
    } else {
      connectConfiguration.setType("opensearch");
    }
  }

  private static void createOperateMetric(final String tenantId, final OffsetDateTime eventTime)
      throws IOException {
    if (isElasticsearch) {
      final var indexDescriptors =
          new IndexDescriptors(connectConfiguration.getIndexPrefix(), true);
      final var metricIndex = indexDescriptors.get(MetricIndex.class);
      createDocumentsES(metricIndex, tenantId, eventTime);
    } else {
      final var indexDescriptors =
          new IndexDescriptors(connectConfiguration.getIndexPrefix(), false);
      final var metricIndex = indexDescriptors.get(MetricIndex.class);
      createDocumentsOS(metricIndex, tenantId, eventTime);
    }
  }

  private static MetricEntity createMetricEntity(
      final String tenantId, final OffsetDateTime eventTime) {
    return new MetricEntity()
        .setValue(String.valueOf(System.currentTimeMillis()))
        .setEvent(MetricsStore.EVENT_PROCESS_INSTANCE_STARTED)
        .setTenantId(tenantId)
        .setEventTime(eventTime);
  }

  private static void createDocumentsES(
      final MetricIndex metricIndex, final String tenantId, final OffsetDateTime eventTime)
      throws IOException {
    try (final var client = new ElasticsearchConnector(connectConfiguration).createClient()) {
      client.index(
          b ->
              b.index(metricIndex.getFullQualifiedName())
                  .document(createMetricEntity(tenantId, eventTime))
                  .refresh(co.elastic.clients.elasticsearch._types.Refresh.True));
    }
  }

  private static void createDocumentsOS(
      final MetricIndex metricIndex, final String tenantId, final OffsetDateTime eventTime)
      throws IOException {
    final var client = new OpensearchConnector(connectConfiguration).createClient();
    client.index(
        b ->
            b.index(metricIndex.getFullQualifiedName())
                .document(createMetricEntity(tenantId, eventTime))
                .refresh(org.opensearch.client.opensearch._types.Refresh.True));
  }

  @Test
  void shouldMigrateOlderMetrics() throws IOException {
    // given
    for (int i = 0; i < 10; i++) {
      createOperateMetric("test1", NOW.minusDays(5));
    }

    // when
    startUsageMetricMigration();
    waitForUsageMetrics(
        camundaClient, "test1", f -> assertThat(f.getProcessInstances()).isEqualTo(10));

    // then
    final var actual =
        camundaClient
            .newUsageMetricsRequest(NOW.minusDays(10), NOW.plusDays(1))
            .withTenants(true)
            .tenantId("test1")
            .send()
            .join();
    assertThat(actual)
        .isEqualTo(
            new UsageMetricsStatisticsImpl(
                10, 0, 0, 1, Map.of("test1", new UsageMetricsStatisticsItemImpl(10, 0, 0))));
  }

  @Test
  void shouldNotMigrateSameMetrics() {
    // when
    startUsageMetricMigration();

    // then
    final var actual =
        camundaClient
            .newUsageMetricsRequest(NOW.minusDays(10), NOW.plusDays(1))
            .withTenants(true)
            .tenantId(TENANT_DEFAULT)
            .send()
            .join();
    assertThat(actual)
        .isEqualTo(
            new UsageMetricsStatisticsImpl(
                3, 0, 0, 1, Map.of(TENANT_DEFAULT, new UsageMetricsStatisticsItemImpl(3, 0, 0))));
  }

  @Test
  void shouldNotMigrateSameMetricsTwice() throws IOException, InterruptedException {
    // given
    createOperateMetric("test2", NOW.minusDays(5));
    startUsageMetricMigration();
    waitForUsageMetrics(
        camundaClient, "test2", f -> assertThat(f.getProcessInstances()).isEqualTo(1));

    // when
    startUsageMetricMigration();
    Thread.sleep(EXPORT_INTERVAL);

    // then
    final var actual =
        camundaClient
            .newUsageMetricsRequest(NOW.minusDays(10), NOW.plusDays(1))
            .withTenants(true)
            .tenantId("test2")
            .send()
            .join();
    assertThat(actual)
        .isEqualTo(
            new UsageMetricsStatisticsImpl(
                1, 0, 0, 1, Map.of("test2", new UsageMetricsStatisticsItemImpl(1, 0, 0))));
  }

  @Test
  void shouldNotMigrateTooOldMetrics() throws IOException, InterruptedException {
    // given
    createOperateMetric("test3", NOW.minusYears(2).minusDays(1));

    // when
    startUsageMetricMigration();
    Thread.sleep(EXPORT_INTERVAL);

    // then
    final var actual =
        camundaClient
            .newUsageMetricsRequest(NOW.minusYears(3), NOW.plusDays(1))
            .withTenants(true)
            .tenantId("test3")
            .send()
            .join();
    assertThat(actual).isEqualTo(new UsageMetricsStatisticsImpl(0, 0, 0, 0, Map.of()));
  }
}
