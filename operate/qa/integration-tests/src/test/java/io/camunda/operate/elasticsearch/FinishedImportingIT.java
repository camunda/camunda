/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import static io.camunda.operate.Metrics.GAUGE_NAME_IMPORT_POSITION_COMPLETED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.operate.Metrics;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.TestSupport;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.RecordsReaderHolder;
import io.camunda.operate.zeebeimport.elasticsearch.ElasticsearchRecordsReader;
import io.camunda.webapps.schema.descriptors.index.ImportPositionIndex;
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
import java.util.Map;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FinishedImportingIT extends OperateZeebeAbstractIT {
  private static final ElasticsearchExporter EXPORTER = new ElasticsearchExporter();
  private static final ElasticsearchExporterConfiguration CONFIG =
      new ElasticsearchExporterConfiguration();

  @Autowired private OperateProperties operateProperties;
  @Autowired private RecordsReaderHolder recordsReaderHolder;
  @Autowired private ImportPositionIndex importPositionIndex;
  @Autowired private RestHighLevelClient esClient;
  @Autowired private MeterRegistry registry;
  private final ProtocolFactory factory = new ProtocolFactory();
  private int emptyBatches = 0;

  @Before
  public void beforeEach() {
    operateProperties.getImporter().setImportPositionUpdateInterval(5000);
    CONFIG.setExportLegacyRecords(true);
    CONFIG.index.prefix = operateProperties.getZeebeElasticsearch().getPrefix();
    CONFIG.index.setNumberOfShards(1);
    CONFIG.index.setNumberOfReplicas(0);
    CONFIG.index.createTemplate = true;
    CONFIG.retention.setEnabled(false);
    CONFIG.bulk.size = 1; // force flushing on the first record
    emptyBatches = operateProperties.getImporter().getCompletedReaderMinEmptyBatches();
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
    registry.clear();
  }

  @Test
  public void shouldMarkRecordReadersAsCompletedIf880RecordReceived() throws IOException {

    // given
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(record);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);
    tester.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    EXPORTER.export(record2);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    // receives 8.7 record and marks partition as finished importing
    tester.performOneRoundOfImport();

    // then
    // require multiple checks to avoid race condition. If records are written to zeebe indices and
    // before a refresh, the record reader pulls the import batch is empty so it then says that the
    // record reader is done when it is not.
    for (int i = 0; i < emptyBatches; i++) {
      tester.performOneRoundOfImport();
    }

    // the import position for
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderCompleted("1-process-instance"));
  }

  @Test
  public void shouldMarkMultiplePositionIndexAsCompletedIf880RecordReceived() throws IOException {

    final var processInstanceRecord = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    final var decisionEvalRecord = generateRecord(ValueType.DECISION_EVALUATION, "8.7.0", 1);
    final var decisionRecord = generateRecord(ValueType.DECISION, "8.7.0", 2);
    EXPORTER.export(processInstanceRecord);
    EXPORTER.export(decisionEvalRecord);
    EXPORTER.export(decisionRecord);

    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);
    tester.performOneRoundOfImport();

    // when
    final var newVersionProcessInstanceRecord =
        generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    EXPORTER.export(newVersionProcessInstanceRecord);
    final var newVersionDecisionEvalRecord =
        generateRecord(ValueType.DECISION_EVALUATION, "8.8.0", 1);
    EXPORTER.export(newVersionDecisionEvalRecord);

    for (int i = 0; i <= emptyBatches; i++) {
      // simulate existing decision records left to process so it is not marked as completed
      final var decisionRecord2 = generateRecord(ValueType.DECISION, "8.7.0", 2);
      EXPORTER.export(decisionRecord2);
      esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

      tester.performOneRoundOfImport();
    }

    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () ->
                isRecordReaderCompleted("1-process-instance")
                    && isRecordReaderCompleted("1-decision-evaluation")
                    && !isRecordReaderCompleted("2-decision"));
  }

  @Test
  public void shouldMarkMultiplePartitionsAsCompletedIfTheyReceiveAn880Record() throws IOException {
    // given

    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    final var partitionTwoRecord = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 2);
    EXPORTER.export(record);
    EXPORTER.export(partitionTwoRecord);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);
    tester.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    final var partitionTwoRecord2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 2);
    EXPORTER.export(record2);
    EXPORTER.export(partitionTwoRecord2);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);
    // Import 8.8 records to trigger importer's counting of empty batches
    tester.performOneRoundOfImport();

    // Import empty batches
    for (int i = 0; i < emptyBatches; i++) {
      tester.performOneRoundOfImport();
    }

    // the import position for
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderCompleted("1-process-instance"));
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderCompleted("2-process-instance"));
  }

  @Test
  public void shouldNotMarkedAsCompletedViaMetricsWhenImportingIsNotDone() throws IOException {
    // given

    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    final var partitionTwoRecord = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 2);
    EXPORTER.export(record);
    EXPORTER.export(partitionTwoRecord);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);
    tester.performOneRoundOfImport();

    // when
    for (int i = 0; i <= emptyBatches; i++) {
      tester.performOneRoundOfImport();
    }

    // then
    await()
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
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
  public void shouldMarkImporterCompletedViaMetricsAsWell()
      throws IOException, InterruptedException {
    // given

    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    final var partitionTwoRecord = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 2);
    EXPORTER.export(record);
    EXPORTER.export(partitionTwoRecord);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);
    tester.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    final var partitionTwoRecord2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 2);
    EXPORTER.export(record2);
    EXPORTER.export(partitionTwoRecord2);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    // Import 8.8 records to trigger importer's counting of empty batches
    tester.performOneRoundOfImport();

    // Import empty batches
    for (int i = 0; i < emptyBatches; i++) {
      tester.performOneRoundOfImport();
    }

    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderCompleted("1-process-instance"));
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderCompleted("2-process-instance"));

    // then

    await()
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var metrics = beanFactory.getBean(Metrics.class);
              final Gauge partitionOneImportStatus = getGauge(metrics, "1");
              final Gauge partitionTwoImportStatus = getGauge(metrics, "2");
              assertThat(partitionOneImportStatus.value()).isEqualTo(1.0);
              assertThat(partitionTwoImportStatus.value()).isEqualTo(1.0);
            });
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

  @Test
  public void shouldNotSetCompletedToFalseForSubsequentRecordsAfterImportingDone()
      throws IOException {

    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(record);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);
    tester.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    EXPORTER.export(record2);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    // receives 8.7 record and marks partition as finished importing
    tester.performOneRoundOfImport();

    // then
    // Require multiple checks to avoid race condition.
    // Otherwise: If records are written to zeebe indices and before a refresh, the record reader
    // pulls an empty import batch, then it might assume falsely
    // that it is done, while it is not.
    for (int i = 0; i < emptyBatches; i++) {
      tester.performOneRoundOfImport();
    }

    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderCompleted("1-process-instance"));

    final var record3 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    EXPORTER.export(record3);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    tester.performOneRoundOfImport();

    await()
        .during(Duration.ofSeconds(10))
        .atMost(Duration.ofSeconds(12))
        .until(() -> isRecordReaderCompleted("1-process-instance"));
  }

  @Test
  public void shouldNotOverwriteImportPositionDocumentWithDefaultValue() throws IOException {

    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(record);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);
    tester.performOneRoundOfImport();

    assertImportPositionMatchesRecord(record, ImportValueType.PROCESS_INSTANCE, 1);

    // simulates restart of zeebe importer by restarting all the record readers
    Arrays.stream(ImportValueType.IMPORT_VALUE_TYPES)
        .forEach(
            type -> {
              final var reader =
                  beanFactory.getBean(
                      ElasticsearchRecordsReader.class,
                      1,
                      type,
                      operateProperties.getImporter().getQueueSize());
              reader.postConstruct();
            });

    assertImportPositionMatchesRecord(record, ImportValueType.PROCESS_INSTANCE, 1);
  }

  @Test
  public void shouldWriteDefaultEmptyDefaultImportPositionDocumentsOnRecordReaderStart() {

    recordsReaderHolder.getAllRecordsReaders().stream()
        .map(ElasticsearchRecordsReader.class::cast)
        .forEach(ElasticsearchRecordsReader::postConstruct);

    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              final var searchRequest =
                  new SearchRequest(importPositionIndex.getFullQualifiedName());
              searchRequest.source().size(100);

              final var documents = esClient.search(searchRequest, RequestOptions.DEFAULT);

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
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    // when
    for (int i = 0; i <= emptyBatches; i++) {
      tester.performOneRoundOfImport();
    }

    // then
    for (final var type : ImportValueType.IMPORT_VALUE_TYPES) {
      await()
          .atMost(Duration.ofSeconds(30))
          .until(() -> isRecordReaderCompleted("1-" + type.getAliasTemplate()));
    }
  }

  private void assertImportPositionMatchesRecord(
      final Record<RecordValue> record, final ImportValueType type, final int partitionId) {
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var req =
                  new GetRequest(
                      importPositionIndex.getFullQualifiedName(),
                      partitionId + "-" + type.getAliasTemplate());
              final var importPositionDoc = esClient.get(req, RequestOptions.DEFAULT);

              if (!importPositionDoc.isExists()) {
                return;
              }

              assertThat(importPositionDoc.getSourceAsMap().get("position"))
                  .isEqualTo(record.getPosition());
            });
  }

  private boolean isRecordReaderCompleted(final String partitionIdFieldValue) throws IOException {
    final var hits =
        Arrays.stream(
                esClient
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
            .filter(hit -> hit.get(ImportPositionIndex.ID).equals(partitionIdFieldValue))
            .findFirst()
            .orElse(Map.of())
            .getOrDefault(ImportPositionIndex.COMPLETED, false);
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
