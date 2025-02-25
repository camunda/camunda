/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.opensearch;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.operate.conditions.DatabaseCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.TestSupport;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.RecordsReaderHolder;
import io.camunda.operate.zeebeimport.opensearch.OpensearchRecordsReader;
import io.camunda.webapps.schema.descriptors.operate.index.ImportPositionIndex;
import io.camunda.webapps.schema.entities.operate.ImportPositionEntity;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporter;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration;
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
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchRequest.Builder;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Opensearch operate tests are not executed in CI, to test locally start a container with docker
 * run -p 9200:9200 -p 9600:9600 -e "discovery.type=single-node" -e "plugins.security.disabled=true"
 * -e "OPENSEARCH_INITIAL_ADMIN_PASSWORD=APassword321?" opensearchproject/opensearch:latest
 */
@TestPropertySource(properties = DatabaseCondition.DATABASE_PROPERTY + "=opensearch")
public class OpensearchFinishedImportingIT extends OperateZeebeAbstractIT {

  private static final OpensearchExporter EXPORTER = new OpensearchExporter();
  private static final OpensearchExporterConfiguration CONFIG =
      new OpensearchExporterConfiguration();

  @Autowired public SchemaManager schemaManager;
  @Autowired private OperateProperties operateProperties;
  @Autowired private RecordsReaderHolder recordsReaderHolder;
  @Autowired private ImportPositionIndex importPositionIndex;
  @Autowired private RichOpenSearchClient osClient;
  private final ProtocolFactory factory = new ProtocolFactory();
  private int emptyBatches;

  @Before
  public void beforeEach() {
    operateProperties.getImporter().setImportPositionUpdateInterval(1000);

    CONFIG.index.prefix = operateProperties.getZeebeOpensearch().getPrefix();
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
            .setConfiguration(new ExporterTestConfiguration<>("opensearch", CONFIG));
    EXPORTER.configure(exporterTestContext);
    EXPORTER.open(new ExporterTestController());

    recordsReaderHolder.resetCountEmptyBatches();
    recordsReaderHolder.resetPartitionsCompletedImporting();
  }

  @Test
  public void shouldMarkRecordReadersAsCompletedIf880RecordReceived() throws IOException {
    // given
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(record);
    osClient.index().refresh("*");
    tester.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    EXPORTER.export(record2);
    osClient.index().refresh("*");

    // receives 8.7 record and marks partition as finished importing
    tester.performOneRoundOfImport();

    // then
    // require multiple checks to avoid race condition. If records are written to zeebe indices and
    // before a refresh, the record reader pulls the import batch is empty so it then says that the
    // record reader is done when it is not.
    for (int i = 0; i < emptyBatches; i++) {
      tester.performOneRoundOfImport();
    }

    Awaitility.await()
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

    osClient.index().refresh("*");
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
      osClient.index().refresh("*");

      tester.performOneRoundOfImport();
    }

    Awaitility.await()
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
    osClient.index().refresh("*");
    tester.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    final var partitionTwoRecord2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 2);
    EXPORTER.export(record2);
    EXPORTER.export(partitionTwoRecord2);
    osClient.index().refresh("*");

    // Import 8.8 records to trigger importer's counting of empty batches
    tester.performOneRoundOfImport();

    // Import empty batches
    for (int i = 0; i < emptyBatches; i++) {
      tester.performOneRoundOfImport();
    }

    // the import position for
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderCompleted("1-process-instance"));
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderCompleted("2-process-instance"));
  }

  @Test
  public void shouldNotSetCompletedToFalseForSubsequentRecordsAfterImportingDone()
      throws IOException {
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(record);
    osClient.index().refresh("*");

    tester.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    EXPORTER.export(record2);
    osClient.index().refresh("*");

    // receives 8.7 record and marks partition as finished importing
    tester.performOneRoundOfImport();

    // then
    // require multiple checks to avoid race condition. If records are written to zeebe indices and
    // before a refresh, the record reader pulls the import batch is empty so it then says that the
    // record reader is done when it is not.
    for (int i = 0; i < emptyBatches; i++) {
      tester.performOneRoundOfImport();
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderCompleted("1-process-instance"));

    final var record3 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    EXPORTER.export(record3);
    osClient.index().refresh("*");

    tester.performOneRoundOfImport();

    Awaitility.await()
        .during(Duration.ofSeconds(10))
        .atMost(Duration.ofSeconds(12))
        .until(() -> isRecordReaderCompleted("1-process-instance"));
  }

  @Test
  public void shouldNotOverwriteImportPositionDocumentWithDefaultValue() {
    // given
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(record);
    osClient.index().refresh("*");
    tester.performOneRoundOfImport();

    assertImportPositionMatchesRecord(record, ImportValueType.PROCESS_INSTANCE, 1);

    // when
    // simulates restart of zeebe importer by restarting all the record readers
    Arrays.stream(ImportValueType.IMPORT_VALUE_TYPES)
        .forEach(
            type -> {
              final var reader =
                  beanFactory.getBean(
                      OpensearchRecordsReader.class,
                      1,
                      type,
                      operateProperties.getImporter().getQueueSize());
              reader.postConstruct();
            });

    // then
    assertImportPositionMatchesRecord(record, ImportValueType.PROCESS_INSTANCE, 1);
  }

  @Test
  public void shouldWriteDefaultEmptyDefaultImportPositionDocumentsOnRecordReaderStart() {
    recordsReaderHolder.getAllRecordsReaders().stream()
        .map(OpensearchRecordsReader.class::cast)
        .forEach(OpensearchRecordsReader::postConstruct);

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              final var searchRequestBuilder =
                  new SearchRequest.Builder()
                      .size(100)
                      .index(importPositionIndex.getFullQualifiedName());
              final var documents =
                  osClient.doc().search(searchRequestBuilder, ImportPositionEntity.class);

              // all initial import position documents created for each record reader
              return documents.hits().hits().size()
                  == recordsReaderHolder.getAllRecordsReaders().size();
            });
  }

  @Test
  public void shouldMarkRecordReadersCompletedEvenIfZeebeIndicesDontExist() throws IOException {
    // given
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    EXPORTER.export(record);
    osClient.index().refresh("*");

    // when
    for (int i = 0; i <= emptyBatches; i++) {
      tester.performOneRoundOfImport();
    }

    // then
    for (final var type : ImportValueType.IMPORT_VALUE_TYPES) {
      Awaitility.await()
          .atMost(Duration.ofSeconds(30))
          .until(() -> isRecordReaderCompleted("1-" + type.getAliasTemplate()));
    }
  }

  private void assertImportPositionMatchesRecord(
      final Record<RecordValue> record, final ImportValueType type, final int partitionId) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var importPositionDoc =
                  osClient
                      .doc()
                      .getWithRetries(
                          importPositionIndex.getFullQualifiedName(),
                          partitionId + "-" + type.getAliasTemplate(),
                          ImportPositionEntity.class)
                      .orElse(new ImportPositionEntity());

              assertThat(importPositionDoc.getPosition()).isEqualTo(record.getPosition());
            });
  }

  private boolean isRecordReaderCompleted(final String partitionIdFieldValue) throws IOException {
    final var hits =
        osClient
            .doc()
            .search(
                new Builder().index(importPositionIndex.getFullQualifiedName()).size(100),
                ImportPositionEntity.class)
            .hits()
            .hits()
            .stream()
            .map(Hit::source)
            .toList();
    if (hits.isEmpty()) {
      return false;
    }
    return hits.stream()
        .filter(hit -> hit.getId().equals(partitionIdFieldValue))
        .findFirst()
        .map(ImportPositionEntity::getCompleted)
        .orElse(false);
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
