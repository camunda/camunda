/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.TestSupport;
import io.camunda.operate.zeebeimport.RecordsReaderHolder;
import io.camunda.operate.zeebeimport.ZeebeImporter;
import io.camunda.webapps.schema.descriptors.operate.index.ImportPositionIndex;
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
import java.util.Map;
import org.awaitility.Awaitility;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FinishedImportingIT extends OperateZeebeAbstractIT {
  private static final ElasticsearchExporter EXPORTER = new ElasticsearchExporter();
  private static final ElasticsearchExporterConfiguration CONFIG =
      new ElasticsearchExporterConfiguration();
  @Rule public SearchTestRule searchTestRule = new SearchTestRule();
  @Autowired public SchemaManager schemaManager;
  @Autowired protected ZeebeImporter zeebeImporter;
  @Autowired private RestHighLevelClient esClient;
  @Autowired private OperateProperties operateProperties;
  @Autowired private RecordsReaderHolder recordsReaderHolder;
  @Autowired private ImportPositionIndex importPositionIndex;
  private final ProtocolFactory factory = new ProtocolFactory();

  @Before
  public void beforeEach() {
    operateProperties.getImporter().setImportPositionUpdateInterval(1000);
    CONFIG.index.prefix = operateProperties.getZeebeElasticsearch().getPrefix();
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
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.6.0", 1);
    EXPORTER.export(record);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    zeebeImporter.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(record2);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

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
        .until(() -> isRecordReaderIsCompleted("1-process-instance"));
  }

  @Test
  public void shouldMarkMultiplePositionIndexAsCompletedIf870RecordReceived() throws IOException {
    final var processInstanceRecord = generateRecord(ValueType.PROCESS_INSTANCE, "8.6.0", 1);
    final var decisionEvalRecord = generateRecord(ValueType.DECISION_EVALUATION, "8.6.0", 1);
    final var decisionRecord = generateRecord(ValueType.DECISION, "8.6.0", 1);
    EXPORTER.export(processInstanceRecord);
    EXPORTER.export(decisionEvalRecord);
    EXPORTER.export(decisionRecord);

    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);
    zeebeImporter.performOneRoundOfImport();

    // when
    final var newVersionProcessInstanceRecord =
        generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(newVersionProcessInstanceRecord);
    final var newVersionDecisionEvalRecord =
        generateRecord(ValueType.DECISION_EVALUATION, "8.7.0", 1);
    EXPORTER.export(newVersionDecisionEvalRecord);

    for (int i = 0; i <= RecordsReaderHolder.MINIMUM_EMPTY_BATCHES_FOR_COMPLETED_READER; i++) {
      // simulate existing decision records left to process so it is not marked as completed
      final var decisionRecord2 = generateRecord(ValueType.DECISION, "8.6.0", 1);
      EXPORTER.export(decisionRecord2);
      esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

      zeebeImporter.performOneRoundOfImport();
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () ->
                isRecordReaderIsCompleted("1-process-instance")
                    && isRecordReaderIsCompleted("1-decision-evaluation")
                    && !isRecordReaderIsCompleted("1-decision"));
  }

  @Test
  public void shouldMarkMultiplePartitionsAsCompletedIfTheyReceiveAn870Record() throws IOException {
    // given
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.6.0", 1);
    final var partitionTwoRecord = generateRecord(ValueType.PROCESS_INSTANCE, "8.6.0", 2);
    EXPORTER.export(record);
    EXPORTER.export(partitionTwoRecord);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    zeebeImporter.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    final var partitionTwoRecord2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 2);
    EXPORTER.export(record2);
    EXPORTER.export(partitionTwoRecord2);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

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

  private boolean isRecordReaderIsCompleted(final String partitionIdFieldValue) throws IOException {
    final var hits =
        Arrays.stream(
                esClient
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
