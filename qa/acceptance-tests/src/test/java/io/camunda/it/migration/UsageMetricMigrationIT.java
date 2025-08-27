/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static io.camunda.migration.usagemetric.OperateMetricMigrator.*;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.currentMultiDbDatabaseType;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.commons.migration.MigrationFinishedEvent;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.statistics.response.UsageMetricsStatistics;
import io.camunda.client.api.statistics.response.UsageMetricsStatisticsItem;
import io.camunda.client.impl.statistics.response.UsageMetricsStatisticsImpl;
import io.camunda.client.impl.statistics.response.UsageMetricsStatisticsItemImpl;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.it.util.TestHelper;
import io.camunda.migration.commons.storage.MigrationRepositoryIndex;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.index.UsageMetricIndex;
import io.camunda.webapps.schema.entities.MetricEntity;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestUsageMetricMigrationApp;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
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
  public static final String TENANT_1 = "tenant1";
  public static final String TENANT_2 = "tenant2";

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
  private static IndexDescriptors indexDescriptors;
  private static MigrationRepositoryIndex migrationRepositoryIndex;

  private static void waitForUsageMetrics(
      final CamundaClient camundaClient, final Consumer<UsageMetricsStatistics> fnRequirements) {
    Awaitility.await("should export metrics to secondary storage")
        .atMost(EXPORT_INTERVAL.multipliedBy(2))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newUsageMetricsRequest(NOW.minusYears(1), NOW.plusDays(1))
                            .send()
                            .join())
                    .satisfies(fnRequirements));
  }

  private CompletableFuture<Boolean> startUsageMetricMigration() {
    final var future = new CompletableFuture<Boolean>();
    //noinspection resource
    new TestUsageMetricMigrationApp(connectConfiguration)
        .withAdditionalInitializer(
            applicationContext ->
                applicationContext.addApplicationListener(
                    e -> {
                      if (e instanceof final MigrationFinishedEvent event) {
                        future.complete(event.isSuccess());
                      }
                    }))
        .start();
    return future;
  }

  @BeforeAll
  static void setup() {
    TestHelper.deployResource(camundaClient, "process/service_tasks_v1.bpmn");

    // generate older operate rPI metrics
    connectConfiguration = new SearchEngineConnectProperties();
    connectConfiguration.setIndexPrefix(UsageMetricMigrationIT.class.getSimpleName().toLowerCase());
    isElasticsearch = currentMultiDbDatabaseType() == DatabaseType.ES;
    final SearchEngineClient searchEngineClient;
    if (isElasticsearch) {
      connectConfiguration.setType("elasticsearch");
      final var client = new ElasticsearchConnector(connectConfiguration).createClient();
      searchEngineClient = new ElasticsearchEngineClient(client, new ObjectMapper());
    } else {
      connectConfiguration.setType("opensearch");
      final var client = new OpensearchConnector(connectConfiguration).createClient();
      searchEngineClient = new OpensearchEngineClient(client, new ObjectMapper());
    }

    migrationRepositoryIndex =
        new MigrationRepositoryIndex(connectConfiguration.getIndexPrefix(), isElasticsearch);
    searchEngineClient.createIndex(migrationRepositoryIndex, new IndexConfiguration());
    searchEngineClient.close();
    indexDescriptors = new IndexDescriptors(connectConfiguration.getIndexPrefix(), isElasticsearch);
  }

  private <T extends IndexDescriptor> void cleanUpIndex(final String index) throws IOException {
    if (isElasticsearch) {
      try (final var client = new ElasticsearchConnector(connectConfiguration).createClient()) {
        client.deleteByQuery(d -> d.index(index).query(q -> q.matchAll(b -> b)).refresh(true));
      }
    } else {
      final var client = new OpensearchConnector(connectConfiguration).createClient();
      client.deleteByQuery(d -> d.index(index).query(q -> q.matchAll(b -> b)).refresh(true));
    }
  }

  @AfterEach
  void afterEach() throws IOException {
    cleanUpIndex(indexDescriptors.get(MetricIndex.class).getFullQualifiedName());
    cleanUpIndex(indexDescriptors.get(UsageMetricIndex.class).getFullQualifiedName());
    cleanUpIndex(migrationRepositoryIndex.getFullQualifiedName());
  }

  private static void createOperateMetric(
      final String tenantId, final OffsetDateTime eventTime, final String event)
      throws IOException {
    if (isElasticsearch) {
      final var indexDescriptors =
          new IndexDescriptors(connectConfiguration.getIndexPrefix(), true);
      final var metricIndex = indexDescriptors.get(MetricIndex.class);
      createDocumentsES(metricIndex, tenantId, eventTime, event);
    } else {
      final var indexDescriptors =
          new IndexDescriptors(connectConfiguration.getIndexPrefix(), false);
      final var metricIndex = indexDescriptors.get(MetricIndex.class);
      createDocumentsOS(metricIndex, tenantId, eventTime, event);
    }
  }

  private static MetricEntity createMetricEntity(
      final String tenantId, final OffsetDateTime eventTime, final String event) {
    return new MetricEntity()
        .setValue(String.valueOf(System.currentTimeMillis()))
        .setEvent(event)
        .setTenantId(tenantId)
        .setEventTime(eventTime);
  }

  private static void createDocumentsES(
      final MetricIndex metricIndex,
      final String tenantId,
      final OffsetDateTime eventTime,
      final String event)
      throws IOException {
    try (final var client = new ElasticsearchConnector(connectConfiguration).createClient()) {
      client.index(
          b ->
              b.index(metricIndex.getFullQualifiedName())
                  .document(createMetricEntity(tenantId, eventTime, event))
                  .refresh(co.elastic.clients.elasticsearch._types.Refresh.True));
    }
  }

  private static void createDocumentsOS(
      final MetricIndex metricIndex,
      final String tenantId,
      final OffsetDateTime eventTime,
      final String event)
      throws IOException {
    final var client = new OpensearchConnector(connectConfiguration).createClient();
    client.index(
        b ->
            b.index(metricIndex.getFullQualifiedName())
                .document(createMetricEntity(tenantId, eventTime, event))
                .refresh(org.opensearch.client.opensearch._types.Refresh.True));
  }

  @Test
  void shouldMigrateMetrics() throws IOException {
    // given
    final var minus2Days = NOW.minusDays(2);
    final var minus5Days = NOW.minusDays(5);
    createOperateMetric(TENANT_1, minus5Days, EVENT_PROCESS_INSTANCE_STARTED);
    createOperateMetric(TENANT_2, minus2Days, EVENT_PROCESS_INSTANCE_STARTED);
    createOperateMetric(TENANT_1, minus5Days, EVENT_DECISION_INSTANCE_EVALUATED);

    // when
    startUsageMetricMigration().join();

    // then
    assertMetrics(
        2,
        1,
        2,
        Map.of(
            TENANT_2, new UsageMetricsStatisticsItemImpl(1, 0, 0),
            TENANT_1, new UsageMetricsStatisticsItemImpl(1, 1, 0)));
    assertMetrics(
        minus2Days, 1, 0, 1, Map.of(TENANT_2, new UsageMetricsStatisticsItemImpl(1, 0, 0)));
  }

  @Test
  void shouldNotMigrateSameMetricsTwice() throws IOException {
    // given
    createOperateMetric(TENANT_1, NOW.minusDays(5), EVENT_PROCESS_INSTANCE_STARTED);
    startUsageMetricMigration().join();
    assertMetrics(1, 0, 1, Map.of(TENANT_1, new UsageMetricsStatisticsItemImpl(1, 0, 0)));

    // when
    cleanUpIndex(migrationRepositoryIndex.getFullQualifiedName());
    startUsageMetricMigration().join();

    // then
    assertMetrics(1, 0, 1, Map.of(TENANT_1, new UsageMetricsStatisticsItemImpl(1, 0, 0)));
  }

  @Test
  void shouldNotMigrateWhenMigrationStepIsAlreadyApplied() throws IOException {
    // given
    createOperateMetric(TENANT_1, NOW.minusDays(7), EVENT_PROCESS_INSTANCE_STARTED);
    createOperateMetric(TENANT_2, NOW.minusDays(7), EVENT_DECISION_INSTANCE_EVALUATED);
    startUsageMetricMigration().join();
    createOperateMetric(TENANT_1, NOW.minusDays(6), EVENT_PROCESS_INSTANCE_STARTED);
    createOperateMetric(TENANT_2, NOW.minusDays(8), EVENT_DECISION_INSTANCE_EVALUATED);

    // when
    createOperateMetric(TENANT_1, NOW.minusDays(6), EVENT_PROCESS_INSTANCE_STARTED);
    startUsageMetricMigration().join();

    // then
    assertMetrics(
        1,
        1,
        2,
        Map.of(
            TENANT_2, new UsageMetricsStatisticsItemImpl(0, 1, 0),
            TENANT_1, new UsageMetricsStatisticsItemImpl(1, 0, 0)));
  }

  @Test
  void shouldNotMigrateTooOldMetrics() throws IOException {
    // given
    final var olderThan2Years = NOW.minusYears(2).minusDays(1);
    createOperateMetric(TENANT_1, olderThan2Years, EVENT_PROCESS_INSTANCE_STARTED);
    createOperateMetric(TENANT_1, olderThan2Years, EVENT_DECISION_INSTANCE_EVALUATED);

    // when
    startUsageMetricMigration().join();

    // then
    assertMetrics(0, 0, 0, Map.of());
  }

  private static void assertMetrics(
      final int rpi,
      final int edi,
      final int at,
      final Map<String, UsageMetricsStatisticsItem> tenants) {
    assertMetrics(NOW.minusYears(10), rpi, edi, at, tenants);
  }

  private static void assertMetrics(
      final OffsetDateTime startTime,
      final int rpi,
      final int edi,
      final int at,
      final Map<String, UsageMetricsStatisticsItem> tenants) {
    final var actual =
        camundaClient
            .newUsageMetricsRequest(startTime, NOW.plusDays(1))
            .withTenants(tenants != null)
            .send()
            .join();
    assertThat(actual)
        .isEqualTo(
            new UsageMetricsStatisticsImpl(rpi, edi, 0, at, tenants != null ? tenants : Map.of()));
  }
}
