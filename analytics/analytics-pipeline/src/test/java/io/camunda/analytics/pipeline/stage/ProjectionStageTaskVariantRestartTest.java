/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.analytics.projection.ProjectionMetrics;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.serving.catalog.DatasetCatalog;
import io.camunda.analytics.serving.spi.TableFetch;
import io.camunda.analytics.serving.spi.TableRow;
import io.camunda.analytics.store.rdbms.RdbmsDatasetStore;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Stage-1 variant accumulator across a restart: the per-instance {@code elementId → count}
 * entries live in the same per-partition RocksDB as the element rows and commit inside the same
 * atomic cut — so a task reopened over the committed state derives the end fact's variant from
 * elements folded on <em>both</em> sides of the restart, and the variant-catalog table receives the
 * full canonical element list.
 */
final class ProjectionStageTaskVariantRestartTest {

  private static final long PI_KEY = 123L;
  private static final int ZEEBE_PARTITION = 3;
  private static final int EB_PARTITION = 1;

  @TempDir Path stateDir;

  private TestMetadataStore metadataStore;
  private DatasetRegistry registry;
  private JdbcDataSource dataSource;
  private long tableId;
  private RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;
  private RdbmsDatasetStore datasetStore;
  private ProjectionStageTask task;

  @BeforeEach
  void setUp() {
    metadataStore = new TestMetadataStore();
    registry = new DatasetRegistry();
    // The variant dictionary as a declared TABLE, mirroring the standard catalog's shape.
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder("variant-catalog", FactType.PROCESS_INSTANCE)
            .filterNotNull("variantHash")
            .asTable("variantHash")
            .dimension("variantHash", DimensionType.LONG)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .dimension("variantElements", DimensionType.TEXT)
            .build();
    tableId = registry.admit(declaration, Map.of(), 0L).cubeId();
    metadataStore.datasetSpecStore().create(registry.get(tableId).orElseThrow());
    // One shared serving database across the restart (the store is closed with each task).
    dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:variantrestart-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
  }

  @AfterEach
  void tearDown() {
    if (task != null) {
      task.close();
    }
  }

  @Test
  void shouldDeriveTheVariantFromElementsFoldedOnBothSidesOfARestart() {
    // given an instance whose first element folds before a committed cut
    openTask();
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 10L, 100L));
    task.process(element(400L, "register", ProcessInstanceIntent.ELEMENT_ACTIVATED, 11L, 101L));
    Cuts.commit(task, 101L);

    // when the task restarts over the committed state and the instance finishes afterwards
    task.close();
    openTask();
    task.process(element(401L, "notify", ProcessInstanceIntent.ELEMENT_ACTIVATED, 20L, 102L));
    task.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 21L, 103L));
    Cuts.commit(task, 103L);

    // then the end fact's variant covered BOTH elements — the pre-restart entry survived inside
    // the cut — and the dictionary row carries the full canonical list
    final List<TableRow> rows =
        datasetStore.queryClient().fetchRows(new TableFetch(compiledTable(), List.of(), 10));
    assertThat(rows)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.values().get("variantElements")).isEqualTo("notify, register");
              assertThat(row.values().get("bpmnProcessId")).isEqualTo("order");
            });
  }

  private CompiledTable compiledTable() {
    final DatasetCompiler compiler =
        new DatasetCompiler(
            MeterCatalog.withDefaults(), new MeterIdRegistry(metadataStore.meterIdStore()));
    final var registered = metadataStore.datasetSpecStore().read(tableId).orElseThrow();
    return compiler.compileTable(tableId, registered.declaration());
  }

  private void openTask() {
    final DatasetCatalog catalog = new DatasetCatalog(metadataStore);
    provider =
        RocksDbStateStoreProvider.open(
            new File(stateDir.toFile(), "stage1"), new SimpleMeterRegistry());
    datasetStore = new RdbmsDatasetStore(dataSource);
    task =
        new ProjectionStageTask(
            EB_PARTITION,
            () -> 1L,
            mock(EventBridgeClient.class),
            "facts",
            1,
            1_000,
            1,
            datasetStore,
            datasetStore.writer(),
            provider,
            provider.keyValueStore(
                AnalyticsColumnFamilies.OPEN_SEGMENT, new DbBytes(), new DbBytes()),
            provider.keyValueStore(
                AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong()),
            provider.keyValueStore(
                AnalyticsColumnFamilies.ZEEBE_APPLIED_POSITION, new DbInt(), new DbLong()),
            catalog,
            Long.MAX_VALUE, // no reload in this test
            false,
            ProjectionMetrics.NOOP,
            0L);
    task.init();
  }

  private static SourceRecord process(
      final ProcessInstanceIntent intent, final long zeebePosition, final long ebOffset) {
    return record(PI_KEY, "order", BpmnElementType.PROCESS, -1L, intent, zeebePosition, ebOffset);
  }

  private static SourceRecord element(
      final long elementInstanceKey,
      final String elementId,
      final ProcessInstanceIntent intent,
      final long zeebePosition,
      final long ebOffset) {
    return record(
        elementInstanceKey,
        elementId,
        BpmnElementType.SERVICE_TASK,
        PI_KEY,
        intent,
        zeebePosition,
        ebOffset);
  }

  private static SourceRecord record(
      final long elementInstanceKey,
      final String elementId,
      final BpmnElementType elementType,
      final long flowScopeKey,
      final ProcessInstanceIntent intent,
      final long zeebePosition,
      final long ebOffset) {
    final ProcessInstanceRecord value =
        new ProcessInstanceRecord()
            .setProcessInstanceKey(PI_KEY)
            .setProcessDefinitionKey(77L)
            .setBpmnProcessId("order")
            .setVersion(3)
            .setTenantId("<default>")
            .setElementId(elementId)
            .setFlowScopeKey(flowScopeKey)
            .setBpmnElementType(elementType);
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.PROCESS_INSTANCE)
            .intent(intent);
    final Record<?> record =
        new CopiedRecord<>(
            value,
            metadata,
            elementInstanceKey,
            ZEEBE_PARTITION,
            zeebePosition,
            -1L,
            zeebePosition);
    return new SourceRecord(EB_PARTITION, ebOffset, record);
  }
}
