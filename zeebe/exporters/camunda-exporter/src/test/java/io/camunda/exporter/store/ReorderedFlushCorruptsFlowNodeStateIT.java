/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.cache.TestProcessCache;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.handlers.FlowNodeInstanceFromProcessInstanceHandler;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.utils.CamundaExporterSchemaUtils;
import io.camunda.exporter.utils.ElasticsearchScriptBuilder;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Real-Elasticsearch counterpart of {@link ReorderedFlushCorruptsFlowNodeStateTest}.
 *
 * <p>{@code ReorderedFlushCorruptsFlowNodeStateTest} proves the same corruption mechanism fast,
 * against a fake {@link BatchRequest} that simulates Elasticsearch's partial-{@code doc} merge
 * semantics in Java code. This test proves it is not just a simulation: it drives the real {@link
 * FlowNodeInstanceFromProcessInstanceHandler} and the real {@link ExporterBatchWriter} through the
 * identical arrival-order reordering, but flushes through the real {@link
 * ElasticsearchBatchRequest} against a real Elasticsearch Testcontainer, and asserts on the
 * document fetched back from it.
 *
 * <p>Slow (spins up a container) - keep {@code ReorderedFlushCorruptsFlowNodeStateTest} as the fast
 * unit-level check and run this one whenever the ES-specific merge behaviour itself needs
 * re-verifying.
 */
final class ReorderedFlushCorruptsFlowNodeStateIT {

  private static final long TARGET_KEY = 2251799813744123L;

  @RegisterExtension private static SearchDBExtension searchDB = SearchDBExtension.create();

  private final ProtocolFactory factory = new ProtocolFactory();
  private final TestProcessCache processCache = new TestProcessCache();

  private String indexPrefix;
  private String indexName;
  private FlowNodeInstanceFromProcessInstanceHandler handler;
  private SearchClientAdapter clientAdapter;

  @BeforeEach
  void setUp() throws IOException {
    indexPrefix =
        "reordered-flush-it-" + RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();

    final var config = new ExporterConfiguration();
    config.getConnect().setIndexPrefix(indexPrefix);
    config.getConnect().setUrl(searchDB.esUrl());
    config.getConnect().setType(ConnectionTypes.ELASTICSEARCH.getType());
    config.getConnect().setClusterName(ConnectionTypes.ELASTICSEARCH.name());
    CamundaExporterSchemaUtils.createSchemas(config);

    indexName = new FlowNodeInstanceTemplate(indexPrefix, true).getFullQualifiedName();
    handler = new FlowNodeInstanceFromProcessInstanceHandler(indexName, processCache);
    clientAdapter = new SearchClientAdapter(searchDB.esClient(), searchDB.objectMapper());
  }

  @AfterEach
  void tearDown() throws IOException {
    searchDB.esClient().indices().delete(d -> d.index(indexPrefix + "*"));
  }

  @Test
  void staleActivatingFlushLandingAfterCompletedLeavesActiveWithEndDate() throws IOException {
    // Positions taken from the customer's record stream for the affected element.
    final var activating =
        record(ProcessInstanceIntent.ELEMENT_ACTIVATING, 1444519674L, 1754817083371L);
    final var completed =
        record(ProcessInstanceIntent.ELEMENT_COMPLETED, 1444521318L, 1754817086584L);

    // --- the new leader (D2) exports the full range: ACTIVATING, then COMPLETED -----------
    // Two separate flushes, because ExporterBatchWriter merges per (id, type) inside one batch;
    // with bulk.size=5000 / bulk.delay=1s and a 1642-position gap these land in different bulks.
    flushAsOwnBulk(activating);
    flushAsOwnBulk(completed);

    var doc = fetchTargetDocument();
    assertThat(doc.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(doc.getEndDate()).isNotNull();
    final var endDateAfterCompletion = doc.getEndDate();

    // --- the stale leader (D1) re-exports ACTIVATING and is then closed --------------------
    // CamundaExporter#close() flushes the pending batch, so this write does reach Elasticsearch,
    // and nothing follows it.
    flushAsOwnBulk(activating);

    // --- then: exactly the corrupted Operate document from the ticket, from real ES --------
    doc = fetchTargetDocument();
    assertThat(doc.getState())
        .describedAs("state was flipped back to ACTIVE by the replayed ACTIVATING write")
        .isEqualTo(FlowNodeState.ACTIVE);
    assertThat(doc.getEndDate())
        .describedAs(
            "endDate survives untouched: flush() only puts END_DATE when entity.getEndDate() != null,"
                + " and never clears it - real Elasticsearch partial doc merge leaves the stale"
                + " value in place")
        .isNotNull()
        .isEqualTo(endDateAfterCompletion);
  }

  /**
   * Control: the same two records in log order, in one single bulk, converge correctly on real
   * Elasticsearch - so the corruption is caused by the arrival order across bulks, not by the
   * handler in isolation.
   */
  @Test
  void inOrderExportProducesCompletedDocument() throws IOException {
    final var activating =
        record(ProcessInstanceIntent.ELEMENT_ACTIVATING, 1444519674L, 1754817083371L);
    final var completed =
        record(ProcessInstanceIntent.ELEMENT_COMPLETED, 1444521318L, 1754817086584L);

    flushAsOwnBulk(activating);
    flushAsOwnBulk(completed);

    final var doc = fetchTargetDocument();
    assertThat(doc.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(doc.getEndDate()).isNotNull();
  }

  // --------------------------------------------------------------------------------------------

  private FlowNodeInstanceEntity fetchTargetDocument() throws IOException {
    searchDB.esClient().indices().refresh(r -> r.index(indexName));
    return clientAdapter.get(String.valueOf(TARGET_KEY), indexName, FlowNodeInstanceEntity.class);
  }

  /**
   * Runs one record through a fresh ExporterBatchWriter and flushes it as its own bulk request
   * against the real Elasticsearch container.
   */
  private void flushAsOwnBulk(final Record<ProcessInstanceRecordValue> record) {
    final var writer =
        ExporterBatchWriter.Builder.begin(new CamundaExporterMetrics(new SimpleMeterRegistry()))
            .withHandler(handler)
            .build();
    writer.addRecord(record);
    try {
      writer.flush(
          new ElasticsearchBatchRequest(searchDB.esClient(), new ElasticsearchScriptBuilder()));
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Record<ProcessInstanceRecordValue> record(
      final ProcessInstanceIntent intent, final long position, final long timestamp) {
    final ProcessInstanceRecordValue value =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("affected-service-task")
            .withProcessInstanceKey(2251799813744000L)
            .withProcessDefinitionKey(2251799813700001L)
            .withBpmnProcessId("affected-process")
            .withTenantId("<default>")
            .withFlowScopeKey(2251799813744000L)
            .build();

    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        r ->
            r.withIntent(intent)
                .withKey(TARGET_KEY)
                .withPosition(position)
                .withTimestamp(timestamp)
                .withPartitionId(1)
                .withValue(value));
  }
}
