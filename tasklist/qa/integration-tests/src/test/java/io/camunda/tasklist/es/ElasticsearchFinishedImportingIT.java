/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import static io.camunda.tasklist.Metrics.GAUGE_NAME_IMPORT_POSITION_COMPLETED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.TestSupport;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebeimport.RecordsReaderAbstract;
import io.camunda.tasklist.zeebeimport.RecordsReaderHolder;
import io.camunda.tasklist.zeebeimport.ZeebeImporter;
import io.camunda.tasklist.zeebeimport.es.RecordsReaderElasticSearch;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import org.awaitility.Awaitility;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ElasticsearchFinishedImportingIT extends TasklistZeebeIntegrationTest {

  private static final ElasticsearchExporter EXPORTER = new ElasticsearchExporter();
  private static final ElasticsearchExporterConfiguration CONFIG =
      new ElasticsearchExporterConfiguration();
  @Autowired protected ZeebeImporter zeebeImporter;
  @Autowired private RestHighLevelClient tasklistEsClient;
  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private RecordsReaderHolder recordsReaderHolder;
  @Autowired private TasklistImportPositionIndex importPositionIndex;
  @Autowired private MeterRegistry meterRegistry;
  private final ProtocolFactory factory = new ProtocolFactory();
  private int emptyBatches;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isElasticSearch());
  }

  @BeforeEach
  public void beforeEach() {
    tasklistProperties.getImporter().setImportPositionUpdateInterval(5000);
    CONFIG.index.prefix = tasklistProperties.getZeebeElasticsearch().getPrefix();
    CONFIG.setExportLegacyRecords(true);
    CONFIG.index.setNumberOfShards(1);
    CONFIG.index.setNumberOfReplicas(0);
    CONFIG.index.createTemplate = true;
    CONFIG.retention.setEnabled(false);
    CONFIG.bulk.size = 1; // force flushing on the first record
    emptyBatches = tasklistProperties.getImporter().getCompletedReaderMinEmptyBatches();
    // here enable all indexes that needed during the tests beforehand as they will be created once
    TestSupport.provideValueTypes()
        .forEach(valueType -> TestSupport.setIndexingForValueType(CONFIG.index, valueType, true));

    final var exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("elastic", CONFIG));
    EXPORTER.configure(exporterTestContext);
    EXPORTER.open(new ExporterTestController());

    recordsReaderHolder.resetCountEmptyBatches();
    recordsReaderHolder.resetPartitionsCompletedImporting();
    meterRegistry.clear();
  }

  @Test
  public void shouldMarkRecordReadersAsCompletedIf880RecordReceived() throws IOException {
    // given
    final var record = generateRecord(ValueType.JOB, "8.7.0", 1);
    EXPORTER.export(record);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    zeebeImporter.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.JOB, "8.8.0", 1);
    EXPORTER.export(record2);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    // receives 8.7 record and marks partition as finished importing
    zeebeImporter.performOneRoundOfImport();

    // then
    // require multiple checks to avoid race condition. If records are written to zeebe indices and
    // before a refresh, the record reader pulls the import batch is empty so it then says that the
    // record reader is done when it is not.
    for (int i = 0; i < emptyBatches; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    // the import position for
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderIsCompleted("1-job"));
  }

  @Test
  public void shouldMarkMultiplePositionIndexAsCompletedIf880RecordReceived() throws IOException {
    final var processInstanceRecord = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    final var jobRecord = generateRecord(ValueType.JOB, "8.7.0", 1);
    final var variableRecord = generateRecord(ValueType.VARIABLE, "8.7.0", 2);
    EXPORTER.export(processInstanceRecord);
    EXPORTER.export(jobRecord);
    EXPORTER.export(variableRecord);

    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);
    zeebeImporter.performOneRoundOfImport();

    // when
    final var newVersionProcessInstanceRecord =
        generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    EXPORTER.export(newVersionProcessInstanceRecord);
    final var newVersionJobRecord = generateRecord(ValueType.JOB, "8.8.0", 1);
    EXPORTER.export(newVersionJobRecord);

    for (int i = 0; i <= emptyBatches; i++) {
      // simulate existing variable records left to process so it is not marked as completed
      final var decisionRecord2 = generateRecord(ValueType.VARIABLE, "8.7.0", 2);
      EXPORTER.export(decisionRecord2);
      tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

      zeebeImporter.performOneRoundOfImport();
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () ->
                isRecordReaderIsCompleted("1-process-instance")
                    && isRecordReaderIsCompleted("1-job")
                    && !isRecordReaderIsCompleted("2-variable"));
  }

  @Test
  public void shouldMarkMultiplePartitionsAsCompletedIfTheyReceiveAn880Record() throws IOException {
    // given
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    final var partitionTwoRecord = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 2);
    EXPORTER.export(record);
    EXPORTER.export(partitionTwoRecord);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    zeebeImporter.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    final var partitionTwoRecord2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 2);
    EXPORTER.export(record2);
    EXPORTER.export(partitionTwoRecord2);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    for (int i = 0; i <= emptyBatches; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    // the import position for
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderIsCompleted("1-process-instance"));
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderIsCompleted("2-process-instance"));
  }

  @Test
  public void shouldNotSetCompletedToFalseForSubsequentRecordsAfterImportingDone()
      throws IOException {
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(record);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    zeebeImporter.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    EXPORTER.export(record2);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    // receives 8.7 record and marks partition as finished importing
    zeebeImporter.performOneRoundOfImport();

    // then
    // require multiple checks to avoid race condition. If records are written to zeebe indices and
    // before a refresh, the record reader pulls the import batch is empty so it then says that the
    // record reader is done when it is not.
    for (int i = 0; i < emptyBatches; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderIsCompleted("1-process-instance"));

    final var record3 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    EXPORTER.export(record3);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    zeebeImporter.performOneRoundOfImport();

    Awaitility.await()
        .during(Duration.ofSeconds(10))
        .atMost(Duration.ofSeconds(12))
        .until(() -> isRecordReaderIsCompleted("1-process-instance"));
  }

  @Test
  public void shouldNotMarkedAsCompletedViaMetricsWhenImportingIsNotDone() throws IOException {
    // given
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    final var partitionTwoRecord = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 2);
    EXPORTER.export(record);
    EXPORTER.export(partitionTwoRecord);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    // when
    for (int i = 0; i <= emptyBatches; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    // then
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var metrics = beanFactory.getBean(Metrics.class);
              final Gauge partitionOneImportStatus = getGauge(metrics, "1");
              final Gauge partitionTwoImportStatus = getGauge(metrics, "2");
              assertThat(partitionOneImportStatus.value()).isEqualTo(0.0);
              assertThat(partitionTwoImportStatus.value()).isEqualTo(0.0);
            });
  }

  @Test
  public void shouldMarkImporterCompletedViaMetricsAsWell() throws IOException {
    // given
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    final var partitionTwoRecord = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 2);
    EXPORTER.export(record);
    EXPORTER.export(partitionTwoRecord);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    zeebeImporter.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    final var partitionTwoRecord2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 2);
    EXPORTER.export(record2);
    EXPORTER.export(partitionTwoRecord2);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    for (int i = 0; i <= emptyBatches; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderIsCompleted("1-process-instance"));
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderIsCompleted("2-process-instance"));

    // then

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var metrics = beanFactory.getBean(Metrics.class);
              final Gauge partitionOneImportStatus = getGauge(metrics, "1");
              final Gauge partitionTwoImportStatus = getGauge(metrics, "2");
              assertThat(partitionOneImportStatus.value()).isEqualTo(1.0);
              assertThat(partitionTwoImportStatus.value()).isEqualTo(1.0);
            });
  }

  @Test
  public void shouldNotOverwriteImportPositionDocumentWithDefaultValue() throws IOException {
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(record);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    zeebeImporter.performOneRoundOfImport();

    assertImportPositionMatchesRecord(record, ImportValueType.PROCESS_INSTANCE, 1);

    // simulates restart of zeebe importer by restarting all the record readers
    Arrays.stream(ImportValueType.values())
        .forEach(
            type -> {
              final var reader =
                  beanFactory.getBean(
                      RecordsReaderElasticSearch.class,
                      1,
                      type,
                      tasklistProperties.getImporter().getQueueSize());
              reader.postConstruct();
            });

    assertImportPositionMatchesRecord(record, ImportValueType.PROCESS_INSTANCE, 1);
  }

  private void assertImportPositionMatchesRecord(
      final Record<RecordValue> record, final ImportValueType type, final int partitionId) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var req =
                  new GetRequest(
                      importPositionIndex.getFullQualifiedName(),
                      partitionId + "-" + type.getAliasTemplate());
              final var importPositionDoc = tasklistEsClient.get(req, RequestOptions.DEFAULT);

              if (!importPositionDoc.isExists()) {
                return;
              }

              assertThat(importPositionDoc.getSourceAsMap().get("position"))
                  .isEqualTo(record.getPosition());
            });
  }

  @Test
  public void shouldWriteDefaultEmptyDefaultImportPositionDocumentsOnRecordReaderStart() {
    recordsReaderHolder.getAllRecordsReaders().stream()
        .map(RecordsReaderElasticSearch.class::cast)
        .forEach(RecordsReaderAbstract::postConstruct);

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              final var searchRequest =
                  new SearchRequest(importPositionIndex.getFullQualifiedName());
              searchRequest.source().size(100);

              final var documents = tasklistEsClient.search(searchRequest, RequestOptions.DEFAULT);

              // all initial import position documents created for each record reader
              return documents.getHits().getHits().length
                  == recordsReaderHolder.getAllRecordsReaders().size();
            });
  }

  @Test
  public void shouldMarkRecordReadersCompletedEvenIfZeebeIndicesDontExist() throws IOException {
    // given
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    EXPORTER.export(record);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    // when
    for (int i = 0; i <= emptyBatches; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    // then
    for (final var type : ImportValueType.values()) {
      await()
          .atMost(Duration.ofSeconds(30))
          .until(() -> isRecordReaderIsCompleted("1-" + type.getAliasTemplate()));
    }
  }

  @NotNull
  private static Gauge getGauge(final Metrics metrics, final String partition) {
    return metrics.getGauge(
        GAUGE_NAME_IMPORT_POSITION_COMPLETED,
        Metrics.TAG_KEY_PARTITION,
        partition,
        Metrics.TAG_KEY_IMPORT_POS_ALIAS,
        "process-instance");
  }

  private boolean isRecordReaderIsCompleted(final String partitionIdFieldValue) throws IOException {
    final var hits =
        Arrays.stream(
                tasklistEsClient
                    .search(
                        new SearchRequest(importPositionIndex.getFullQualifiedName())
                            .source(new SearchSourceBuilder().size(100)),
                        RequestOptions.DEFAULT)
                    .getHits()
                    .getHits())
            .map(SearchHit::getSourceAsMap)
            .toList();
    if (hits.isEmpty()) {
      return false;
    }
    return (Boolean)
        hits.stream()
            .filter(hit -> hit.get(TasklistImportPositionIndex.ID).equals(partitionIdFieldValue))
            .findFirst()
            .orElse(new HashMap<>())
            .getOrDefault(TasklistImportPositionIndex.COMPLETED, false);
  }

  private <T extends RecordValue> Record<T> generateRecord(
      final ValueType valueType, final String brokerVersion, final int partitionId) {
    return factory.generateRecord(
        valueType,
        r ->
            r.withBrokerVersion(brokerVersion)
                .withPartitionId(partitionId)
                .withTimestamp(System.currentTimeMillis()));
  }
}
