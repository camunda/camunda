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
import io.camunda.client.CamundaClient;
import io.camunda.client.api.statistics.response.UsageMetricsStatisticsItem;
import io.camunda.client.impl.statistics.response.UsageMetricsStatisticsImpl;
import io.camunda.client.impl.statistics.response.UsageMetricsStatisticsItemImpl;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.migration.commons.configuration.MigrationProperties;
import io.camunda.migration.commons.storage.MigrationRepositoryIndex;
import io.camunda.migration.usagemetric.OperateMetricMigrator;
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
import io.camunda.webapps.schema.descriptors.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.index.UsageMetricIndex;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.webapps.schema.entities.MetricEntity;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
public class OperateMetricMigratorIT {

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

  private static CamundaClient camundaClient;
  private static SearchEngineConnectProperties connectConfiguration;
  private static boolean isElasticsearch;
  private static IndexDescriptors indexDescriptors;
  private static MigrationRepositoryIndex migrationRepositoryIndex;
  private static MetricIndex metricIndex;
  private static ImportPositionIndex importPositionIndex;

  private void startOperateMetricMigrator() {
    try {
      final var operateMetricMigrator =
          new OperateMetricMigrator(
              connectConfiguration, new SimpleMeterRegistry(), new MigrationProperties());
      operateMetricMigrator.call();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeAll
  static void setup() throws IOException {
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("process/service_tasks_v1.bpmn")
        .send()
        .join();
    // generate older operate rPI metrics
    connectConfiguration = new SearchEngineConnectProperties();
    connectConfiguration.setIndexPrefix(
        OperateMetricMigratorIT.class.getSimpleName().toLowerCase());
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

    metricIndex = indexDescriptors.get(MetricIndex.class);
    importPositionIndex =
        new ImportPositionIndex(connectConfiguration.getIndexPrefix(), isElasticsearch);
    createImportPosition();
  }

  private void cleanUpIndex(final String index) throws IOException {
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
    cleanUpIndex(metricIndex.getFullQualifiedName());
    cleanUpIndex(indexDescriptors.get(UsageMetricIndex.class).getFullQualifiedName());
    cleanUpIndex(migrationRepositoryIndex.getFullQualifiedName());
  }

  private static void createImportPosition() throws IOException {
    final var entities =
        List.of(
            importPositions(true, 1, ListViewTemplate.INDEX_NAME),
            importPositions(true, 1, DecisionInstanceTemplate.INDEX_NAME));
    for (final Object entity : entities) {
      if (isElasticsearch) {
        createDocumentsES(importPositionIndex, entity);
      } else {
        createDocumentsOS(importPositionIndex, entity);
      }
    }
  }

  private static ImportPositionEntity importPositions(
      final boolean completed, final int partition, final String indexName) {
    return new ImportPositionEntity()
        .setId(partition + "-" + indexName)
        .setPartitionId(partition)
        .setAliasName(indexName)
        .setIndexName(indexName)
        .setCompleted(completed);
  }

  private static void createOperateMetric(
      final String tenantId, final OffsetDateTime eventTime, final String event)
      throws IOException {
    if (isElasticsearch) {
      createDocumentsES(metricIndex, createMetricEntity(tenantId, eventTime, event));
    } else {
      createDocumentsOS(metricIndex, createMetricEntity(tenantId, eventTime, event));
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
      final IndexDescriptor indexDescriptor, final Object document) throws IOException {
    try (final var client = new ElasticsearchConnector(connectConfiguration).createClient()) {
      client.index(
          b ->
              b.index(indexDescriptor.getFullQualifiedName())
                  .document(document)
                  .refresh(co.elastic.clients.elasticsearch._types.Refresh.True));
    }
  }

  private static void createDocumentsOS(
      final IndexDescriptor indexDescriptor, final Object document) throws IOException {
    final var client = new OpensearchConnector(connectConfiguration).createClient();
    client.index(
        b ->
            b.index(indexDescriptor.getFullQualifiedName())
                .document(document)
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
    startOperateMetricMigrator();

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
    startOperateMetricMigrator();
    assertMetrics(1, 0, 1, Map.of(TENANT_1, new UsageMetricsStatisticsItemImpl(1, 0, 0)));

    // when
    cleanUpIndex(migrationRepositoryIndex.getFullQualifiedName());
    startOperateMetricMigrator();

    // then
    assertMetrics(1, 0, 1, Map.of(TENANT_1, new UsageMetricsStatisticsItemImpl(1, 0, 0)));
  }

  @Test
  void shouldNotMigrateWhenMigrationStepIsAlreadyApplied() throws IOException {
    // given
    createOperateMetric(TENANT_1, NOW.minusDays(7), EVENT_PROCESS_INSTANCE_STARTED);
    createOperateMetric(TENANT_2, NOW.minusDays(7), EVENT_DECISION_INSTANCE_EVALUATED);
    startOperateMetricMigrator();
    createOperateMetric(TENANT_1, NOW.minusDays(6), EVENT_PROCESS_INSTANCE_STARTED);
    createOperateMetric(TENANT_2, NOW.minusDays(8), EVENT_DECISION_INSTANCE_EVALUATED);

    // when
    createOperateMetric(TENANT_1, NOW.minusDays(6), EVENT_PROCESS_INSTANCE_STARTED);
    startOperateMetricMigrator();

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
    startOperateMetricMigrator();

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
