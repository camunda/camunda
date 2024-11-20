/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.schema.indices.ImportPositionIndex;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.TestSupport;
import io.camunda.tasklist.zeebeimport.RecordsReaderHolder;
import io.camunda.tasklist.zeebeimport.ZeebeImporter;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import org.awaitility.Awaitility;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
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
  @Autowired private ImportPositionIndex importPositionIndex;
  private final ProtocolFactory factory = new ProtocolFactory();

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isElasticSearch());
  }

  @BeforeEach
  public void beforeEach() {
    tasklistProperties.getImporter().setImportPositionUpdateInterval(1000);
    CONFIG.index.prefix = tasklistProperties.getZeebeElasticsearch().getPrefix();
    CONFIG.index.setNumberOfShards(1);
    CONFIG.index.setNumberOfReplicas(0);
    CONFIG.index.createTemplate = true;
    CONFIG.retention.setEnabled(false);
    CONFIG.bulk.size = 1; // force flushing on the first record

    // here enable all indexes that needed during the tests beforehand as they will be created once
    TestSupport.provideValueTypes()
        .forEach(valueType -> TestSupport.setIndexingForValueType(CONFIG.index, valueType, true));

    final var exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("elastic", CONFIG));
    EXPORTER.configure(exporterTestContext);
    EXPORTER.open(new ExporterTestController());

    recordsReaderHolder.resetCountEmptyBatches();
  }

  @Test
  public void shouldMarkRecordReadersAsCompletedIf870RecordReceived() throws IOException {
    // given
    final var record = generateRecord(ValueType.JOB, "8.6.0", 1);
    EXPORTER.export(record);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    zeebeImporter.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.JOB, "8.7.0", 1);
    EXPORTER.export(record2);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    // receives 8.7 record and marks partition as finished importing
    zeebeImporter.performOneRoundOfImport();

    // then
    // require multiple checks to avoid race condition. If records are written to zeebe indices and
    // before a refresh, the record reader pulls the import batch is empty so it then says that the
    // record reader is done when it is not.
    for (int i = 0; i < RecordsReaderHolder.MINIMUM_EMPTY_BATCHES_FOR_COMPLETED_READER; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    // the import position for
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderIsCompleted("1-job"));
  }

  @Test
  public void shouldMarkMultiplePositionIndexAsCompletedIf870RecordReceived() throws IOException {
    final var processInstanceRecord = generateRecord(ValueType.PROCESS_INSTANCE, "8.6.0", 1);
    final var jobRecord = generateRecord(ValueType.JOB, "8.6.0", 1);
    final var variableRecord = generateRecord(ValueType.VARIABLE, "8.6.0", 1);
    EXPORTER.export(processInstanceRecord);
    EXPORTER.export(jobRecord);
    EXPORTER.export(variableRecord);

    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);
    zeebeImporter.performOneRoundOfImport();

    // when
    final var newVersionProcessInstanceRecord =
        generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(newVersionProcessInstanceRecord);
    final var newVersionJobRecord = generateRecord(ValueType.JOB, "8.7.0", 1);
    EXPORTER.export(newVersionJobRecord);

    for (int i = 0; i <= RecordsReaderHolder.MINIMUM_EMPTY_BATCHES_FOR_COMPLETED_READER; i++) {
      // simulate existing variable records left to process so it is not marked as completed
      final var decisionRecord2 = generateRecord(ValueType.VARIABLE, "8.6.0", 1);
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
                    && !isRecordReaderIsCompleted("1-variable"));
  }

  @Test
  public void shouldMarkMultiplePartitionsAsCompletedIfTheyReceiveAn870Record() throws IOException {
    // given
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.6.0", 1);
    final var partitionTwoRecord = generateRecord(ValueType.PROCESS_INSTANCE, "8.6.0", 2);
    EXPORTER.export(record);
    EXPORTER.export(partitionTwoRecord);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    zeebeImporter.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    final var partitionTwoRecord2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 2);
    EXPORTER.export(record2);
    EXPORTER.export(partitionTwoRecord2);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    for (int i = 0; i <= RecordsReaderHolder.MINIMUM_EMPTY_BATCHES_FOR_COMPLETED_READER; i++) {
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
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.6.0", 1);
    EXPORTER.export(record);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    zeebeImporter.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(record2);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    // receives 8.7 record and marks partition as finished importing
    zeebeImporter.performOneRoundOfImport();

    // then
    // require multiple checks to avoid race condition. If records are written to zeebe indices and
    // before a refresh, the record reader pulls the import batch is empty so it then says that the
    // record reader is done when it is not.
    for (int i = 0; i < RecordsReaderHolder.MINIMUM_EMPTY_BATCHES_FOR_COMPLETED_READER; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderIsCompleted("1-process-instance"));

    final var record3 = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(record3);
    tasklistEsClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    zeebeImporter.performOneRoundOfImport();

    Awaitility.await()
        .during(Duration.ofSeconds(10))
        .atMost(Duration.ofSeconds(12))
        .until(() -> isRecordReaderIsCompleted("1-process-instance"));
  }

  private boolean isRecordReaderIsCompleted(final String partitionIdFieldValue) throws IOException {
    final var hits =
        Arrays.stream(
                tasklistEsClient
                    .search(
                        new SearchRequest(importPositionIndex.getFullQualifiedName()),
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
            .orElse(new HashMap<>())
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
