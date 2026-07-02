/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

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
import io.camunda.tasklist.zeebeimport.os.RecordsReaderOpenSearch;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporter;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration;
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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchRequest.Builder;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.RefreshRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Mirrors {@code operate}'s {@code OpensearchFinishedImportingIT}: in a real 8.7 -> 8.8 upgrade the
 * only value type that receives an 8.8 record is {@link ValueType#TENANT} (auto-exported on
 * upgrade, PR #38541); every other entity stops at 8.7. A record reader is therefore marked
 * completed once <em>any</em> import-position document on its partition carries an 8.8 {@code
 * indexName} (the tenant one) and the reader has since seen {@code completedReaderMinEmptyBatches}
 * empty batches. Because completion is re-derived from the <em>persisted</em> import position
 * (which is flushed asynchronously), the empty batches must only be counted after the 8.8 indexName
 * has been persisted - hence {@link #waitForImportPositionToNotNull} between the 8.8 round and the
 * empty rounds (see #56595).
 */
public class OpensearchFinishedImportingIT extends TasklistZeebeIntegrationTest {
  private static final OpensearchExporter EXPORTER = new OpensearchExporter();
  private static final OpensearchExporterConfiguration CONFIG =
      new OpensearchExporterConfiguration();

  @Autowired protected ZeebeImporter zeebeImporter;

  @Autowired
  @Qualifier("tasklistOsClient")
  OpenSearchClient openSearchClient;

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private RecordsReaderHolder recordsReaderHolder;
  @Autowired private TasklistImportPositionIndex importPositionIndex;
  @Autowired private MeterRegistry meterRegistry;
  private final ProtocolFactory factory = new ProtocolFactory();
  private int emptyBatches;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isOpenSearch());
  }

  @DynamicPropertySource
  static void setProperties(final DynamicPropertyRegistry registry) {
    registry.add("camunda.tasklist.importer-enabled", () -> true);
  }

  @BeforeEach
  public void beforeEach() throws IOException {
    tasklistProperties.getImporter().setImportPositionUpdateInterval(1000);
    CONFIG.setIncludeEnabledRecords(true);
    CONFIG.url = tasklistProperties.getOpenSearch().getUrl();
    CONFIG.index.prefix = tasklistProperties.getZeebeOpenSearch().getPrefix();
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
            .setConfiguration(new ExporterTestConfiguration<>("opensearch", CONFIG));
    EXPORTER.configure(exporterTestContext);
    EXPORTER.open(new ExporterTestController());

    recordsReaderHolder.resetCountEmptyBatches();
    meterRegistry.clear();
    createInitialImportPositionDocuments();
  }

  @Test
  public void shouldMarkRecordReadersAsCompletedIf880RecordReceived() throws IOException {
    // given
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(record);
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());
    zeebeImporter.performOneRoundOfImport();

    // when
    final var record2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    EXPORTER.export(record2);
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());

    // reads the 8.8 record and advances the persisted import position past the 8.8 boundary
    zeebeImporter.performOneRoundOfImport();
    waitForImportPositionToNotNull(1, ImportValueType.PROCESS_INSTANCE);

    // then
    // require multiple checks to avoid race condition. If records are written to zeebe indices and
    // before a refresh, the record reader pulls the import batch is empty so it then says that the
    // record reader is done when it is not.
    for (int i = 0; i < emptyBatches; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderIsCompleted("1-process-instance"));
  }

  @Test
  public void shouldMarkRecordReadersAsCompletedIfTenantRecordReceived() throws IOException {
    // given
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(record);
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());
    zeebeImporter.performOneRoundOfImport();

    // when the only 8.8 record is a TENANT record (as in a real 8.7 -> 8.8 upgrade)
    final var record2 = generateRecord(ValueType.TENANT, "8.8.0", 1);
    EXPORTER.export(record2);
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());
    zeebeImporter.performOneRoundOfImport();
    waitForImportPositionToNotNull(1, ImportValueType.TENANT);

    // then
    for (int i = 0; i < emptyBatches; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    await().atMost(Duration.ofSeconds(30)).until(() -> isRecordReaderIsCompleted("1-tenant"));
  }

  @Test
  @Disabled("Mirrors operate's OpensearchFinishedImportingIT, where this case is disabled")
  public void shouldMarkRecordReadersInSamePartitionAsTenantRecordAsCompleted() throws IOException {
    // given records for two partitions that only ever reach 8.7
    final var processInstanceRecord = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    final var processInstance2Record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 2);
    EXPORTER.export(processInstanceRecord);
    EXPORTER.export(processInstance2Record);
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());
    zeebeImporter.performOneRoundOfImport();

    // when only partition 1 receives the 8.8 TENANT record
    final var tenantRecord = generateRecord(ValueType.TENANT, "8.8.0", 1);
    EXPORTER.export(tenantRecord);
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());
    zeebeImporter.performOneRoundOfImport();
    waitForImportPositionToNotNull(1, ImportValueType.TENANT);

    for (int i = 0; i <= emptyBatches; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    // then the process-instance reader on partition 1 completes off the tenant boundary, while
    // partition 2 (no 8.8 record) does not
    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () ->
                isRecordReaderIsCompleted("1-process-instance")
                    && !isRecordReaderIsCompleted("2-process-instance"));
  }

  @Test
  public void shouldMarkMultiplePartitionsAsCompletedIfTheyReceiveAn880Record() throws IOException {
    // given
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    final var partitionTwoRecord = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 2);
    EXPORTER.export(record);
    EXPORTER.export(partitionTwoRecord);
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());
    zeebeImporter.performOneRoundOfImport();

    // when both partitions reach the 8.8 boundary
    final var tenantRecord = generateRecord(ValueType.TENANT, "8.8.0", 1);
    final var partitionTwoRecord2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 2);
    EXPORTER.export(tenantRecord);
    EXPORTER.export(partitionTwoRecord2);
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());
    zeebeImporter.performOneRoundOfImport();
    waitForImportPositionToNotNull(1, ImportValueType.TENANT);
    waitForImportPositionToNotNull(2, ImportValueType.PROCESS_INSTANCE);

    for (int i = 0; i <= emptyBatches; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    // then
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderIsCompleted("1-process-instance"));
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderIsCompleted("2-process-instance"));
  }

  @Test
  public void shouldCompleteTenantReaderFromPersistedIndexNameWithoutReadingAn88Batch()
      throws IOException {
    // Regression test for #56595 (see ES IT for the full rationale): completion of the partition
    // must be re-derived from the *persisted* import-position state (indexName contains "8.8"), NOT
    // from a transient in-memory flag that is only armed while physically reading a non-empty 8.8
    // batch. This reproduces the post-restart state: the 8.8 boundary exists only in the persisted
    // import-position document and is never observed as a live batch. It fails on the old in-memory
    // design and passes once completion reads persisted state. The default import-position
    // documents were already created in beforeEach via createInitialImportPositionDocuments().

    // given the persisted 1-tenant document carries an 8.8 indexName (simulated pre-restart state),
    // while all in-memory boundary state is empty and no 8.8 batch is ever read
    final var current =
        openSearchClient
            .get(
                new GetRequest.Builder()
                    .index(importPositionIndex.getFullQualifiedName())
                    .id("1-tenant")
                    .build(),
                ImportPositionEntity.class)
            .source();
    current.setIndexName("zeebe-record_tenant_8.8.0_");
    openSearchClient.index(
        i ->
            i.index(importPositionIndex.getFullQualifiedName())
                .id("1-tenant")
                .document(current)
                .refresh(Refresh.True));

    // when only empty import rounds run (nothing is exported, so every batch is empty)
    for (int i = 0; i < emptyBatches; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    // then the tenant reader still completes, purely from persisted state
    await().atMost(Duration.ofSeconds(30)).until(() -> isRecordReaderIsCompleted("1-tenant"));
  }

  @Test
  public void shouldNotMarkedAsCompletedViaMetricsWhenImportingIsNotDone() throws IOException {
    // given only 8.7 records (the 8.8 boundary is never reached)
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    final var partitionTwoRecord = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 2);
    EXPORTER.export(record);
    EXPORTER.export(partitionTwoRecord);
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());

    // when
    for (int i = 0; i <= emptyBatches; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    // then
    await()
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
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());
    zeebeImporter.performOneRoundOfImport();

    // when both partitions reach the 8.8 boundary
    final var tenantRecord = generateRecord(ValueType.TENANT, "8.8.0", 1);
    final var partitionTwoRecord2 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 2);
    EXPORTER.export(tenantRecord);
    EXPORTER.export(partitionTwoRecord2);
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());
    zeebeImporter.performOneRoundOfImport();
    waitForImportPositionToNotNull(1, ImportValueType.TENANT);
    waitForImportPositionToNotNull(2, ImportValueType.PROCESS_INSTANCE);

    for (int i = 0; i < emptyBatches; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderIsCompleted("1-process-instance"));
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderIsCompleted("2-process-instance"));

    // then
    await()
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
  public void shouldNotSetCompletedToFalseForSubsequentRecordsAfterImportingDone()
      throws IOException {
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(record);
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());
    zeebeImporter.performOneRoundOfImport();

    // when the partition reaches the 8.8 boundary via the tenant record
    final var tenantRecord = generateRecord(ValueType.TENANT, "8.8.0", 1);
    EXPORTER.export(tenantRecord);
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());
    zeebeImporter.performOneRoundOfImport();
    waitForImportPositionToNotNull(1, ImportValueType.TENANT);

    // then
    // require multiple checks to avoid race condition. If records are written to zeebe indices and
    // before a refresh, the record reader pulls the import batch is empty so it then says that the
    // record reader is done when it is not.
    for (int i = 0; i < emptyBatches; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> isRecordReaderIsCompleted("1-process-instance"));

    // a subsequent 8.8 record must not reset completed back to false
    final var record3 = generateRecord(ValueType.PROCESS_INSTANCE, "8.8.0", 1);
    EXPORTER.export(record3);
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());
    zeebeImporter.performOneRoundOfImport();

    await()
        .during(Duration.ofSeconds(10))
        .atMost(Duration.ofSeconds(12))
        .until(() -> isRecordReaderIsCompleted("1-process-instance"));
  }

  @Test
  public void shouldNotOverwriteImportPositionDocumentWithDefaultValue() throws IOException {
    // given
    final var record = generateRecord(ValueType.PROCESS_INSTANCE, "8.7.0", 1);
    EXPORTER.export(record);
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());

    zeebeImporter.performOneRoundOfImport();

    assertImportPositionMatchesRecord(record, ImportValueType.PROCESS_INSTANCE, 1);

    // when
    // simulates restart of zeebe importer by restarting all the record readers
    Arrays.stream(ImportValueType.values())
        .forEach(
            type -> {
              final var reader =
                  beanFactory.getBean(
                      RecordsReaderOpenSearch.class,
                      1,
                      type,
                      tasklistProperties.getImporter().getQueueSize());
              reader.postConstruct();
            });

    // then
    assertImportPositionMatchesRecord(record, ImportValueType.PROCESS_INSTANCE, 1);
  }

  @Test
  public void shouldWriteDefaultEmptyDefaultImportPositionDocumentsOnRecordReaderStart() {
    createInitialImportPositionDocuments();
  }

  @Test
  public void shouldMarkRecordReadersCompletedEvenIfZeebeIndicesDontExist() throws IOException {
    // given only a TENANT 8.8 record exists; no other zeebe indices are created
    final var record = generateRecord(ValueType.TENANT, "8.8.0", 1);
    EXPORTER.export(record);
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());

    zeebeImporter.performOneRoundOfImport();
    waitForImportPositionToNotNull(1, ImportValueType.TENANT);

    // when
    for (int i = 0; i <= emptyBatches; i++) {
      zeebeImporter.performOneRoundOfImport();
    }

    // then every reader on the partition completes off the tenant boundary, even those whose zeebe
    // indices never existed
    for (final var type : ImportValueType.values()) {
      await()
          .atMost(Duration.ofSeconds(30))
          .until(() -> isRecordReaderIsCompleted("1-" + type.getAliasTemplate()));
    }
  }

  private void createInitialImportPositionDocuments() {
    recordsReaderHolder.getAllRecordsReaders().stream()
        .map(RecordsReaderOpenSearch.class::cast)
        .forEach(RecordsReaderAbstract::postConstruct);

    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              final var searchRequest =
                  new SearchRequest.Builder()
                      .index(importPositionIndex.getFullQualifiedName())
                      .size(100)
                      .build();
              final var documents =
                  openSearchClient.search(searchRequest, ImportPositionEntity.class);

              return documents.hits().hits().stream()
                      .map(Hit::source)
                      .filter(importPosition -> !importPosition.getCompleted())
                      .count()
                  == recordsReaderHolder.getAllRecordsReaders().size();
            });
  }

  private void assertImportPositionMatchesRecord(
      final Record<RecordValue> record, final ImportValueType type, final int partitionId) {
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var req =
                  new GetRequest.Builder()
                      .index(importPositionIndex.getFullQualifiedName())
                      .id(partitionId + "-" + type.getAliasTemplate())
                      .build();
              final var importPositionDoc = openSearchClient.get(req, ImportPositionEntity.class);

              if (importPositionDoc.source() == null) {
                return;
              }

              assertThat(importPositionDoc.source().getPosition()).isEqualTo(record.getPosition());
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

  /**
   * Waits until the persisted import-position document for the given partition/value-type carries
   * an 8.8 {@code indexName}. Completion is re-derived from persisted state and the import position
   * is flushed asynchronously (importPositionUpdateInterval), so the empty-batch rounds must only
   * run after this returns (see #56595).
   */
  private void waitForImportPositionToNotNull(
      final int partitionId, final ImportValueType importValueType) {
    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              final var indexName =
                  getImportPosition(partitionId + "-" + importValueType.getAliasTemplate())
                      .getIndexName();
              return indexName != null && indexName.contains("8.8");
            });
  }

  private boolean isRecordReaderIsCompleted(final String partitionIdFieldValue) throws IOException {
    return getImportPosition(partitionIdFieldValue).getCompleted();
  }

  private ImportPositionEntity getImportPosition(final String partitionIdFieldValue)
      throws IOException {
    return openSearchClient
        .search(
            new Builder().index(importPositionIndex.getFullQualifiedName()).size(100).build(),
            ImportPositionEntity.class)
        .hits()
        .hits()
        .stream()
        .map(Hit::source)
        .filter(source -> source != null && partitionIdFieldValue.equals(source.getId()))
        .findFirst()
        .orElse(new ImportPositionEntity());
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
