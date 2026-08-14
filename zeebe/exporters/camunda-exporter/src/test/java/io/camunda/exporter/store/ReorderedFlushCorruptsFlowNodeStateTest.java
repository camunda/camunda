/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.exporter.cache.TestProcessCache;
import io.camunda.exporter.handlers.FlowNodeInstanceFromProcessInstanceHandler;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Second half of the SUPPORT-34109 reproduction.
 *
 * <p>{@code DualExporterDirectorReplayRaceTest} (zeebe/broker) proves that two {@link
 * io.camunda.zeebe.broker.exporter.stream.ExporterDirector} instances for one partition can make
 * Elasticsearch observe the {@code ELEMENT_ACTIVATING} write for a flow node instance <em>after</em>
 * the {@code ELEMENT_COMPLETED} write for the same instance.
 *
 * <p>This test takes that arrival order and drives the real {@link
 * FlowNodeInstanceFromProcessInstanceHandler} and the real {@link ExporterBatchWriter} through it,
 * against a {@link BatchRequest} that reproduces Elasticsearch partial-{@code doc} merge semantics
 * (see {@code ElasticsearchBatchRequest#upsertWithRouting}: {@code a.doc(updateFields).upsert(...)},
 * with no {@code ifSeqNo}/{@code ifPrimaryTerm}/version guard). It shows the resulting document is
 * exactly the customer's: {@code state=ACTIVE} with a stale non-null {@code endDate}.
 */
final class ReorderedFlushCorruptsFlowNodeStateTest {

  private static final String INDEX = "operate-flownode-instance-8.3.1_";
  private static final long TARGET_KEY = 2251799813744123L;

  private final ProtocolFactory factory = new ProtocolFactory();
  private final TestProcessCache processCache = new TestProcessCache();
  private final FlowNodeInstanceFromProcessInstanceHandler handler =
      new FlowNodeInstanceFromProcessInstanceHandler(INDEX, processCache);

  /** id -> document. Mutated exactly the way an Elasticsearch partial `doc` update would. */
  private final Map<String, Map<String, Object>> elasticsearch = new HashMap<>();

  @Test
  void staleActivatingFlushLandingAfterCompletedLeavesActiveWithEndDate() {
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

    assertThat(doc()).containsEntry(FlowNodeInstanceTemplate.STATE, FlowNodeState.COMPLETED);
    assertThat(doc().get(FlowNodeInstanceTemplate.END_DATE)).isNotNull();
    final var endDateAfterCompletion = doc().get(FlowNodeInstanceTemplate.END_DATE);

    // --- the stale leader (D1) re-exports ACTIVATING and is then closed --------------------
    // CamundaExporter#close() flushes the pending batch, so this write does reach Elasticsearch,
    // and nothing follows it.
    flushAsOwnBulk(activating);

    // --- then: exactly the corrupted Operate document from the ticket ---------------------
    assertThat(doc())
        .describedAs("state was flipped back to ACTIVE by the replayed ACTIVATING write")
        .containsEntry(FlowNodeInstanceTemplate.STATE, FlowNodeState.ACTIVE);
    assertThat(doc().get(FlowNodeInstanceTemplate.END_DATE))
        .describedAs(
            "endDate survives untouched: flush() only puts END_DATE when entity.getEndDate() != null,"
                + " and never clears it")
        .isNotNull()
        .isEqualTo(endDateAfterCompletion);
  }

  /**
   * Control: the same two records in log order, in one single bulk, converge correctly - so the
   * corruption is caused by the arrival order across bulks, not by the handler in isolation.
   */
  @Test
  void inOrderExportProducesCompletedDocument() {
    final var activating =
        record(ProcessInstanceIntent.ELEMENT_ACTIVATING, 1444519674L, 1754817083371L);
    final var completed =
        record(ProcessInstanceIntent.ELEMENT_COMPLETED, 1444521318L, 1754817086584L);

    flushAsOwnBulk(activating);
    flushAsOwnBulk(completed);

    assertThat(doc()).containsEntry(FlowNodeInstanceTemplate.STATE, FlowNodeState.COMPLETED);
    assertThat(doc().get(FlowNodeInstanceTemplate.END_DATE)).isNotNull();
  }

  // --------------------------------------------------------------------------------------------

  private Map<String, Object> doc() {
    return elasticsearch.get(String.valueOf(TARGET_KEY));
  }

  /** Runs one record through a fresh ExporterBatchWriter and flushes it as its own bulk request. */
  private void flushAsOwnBulk(final Record<ProcessInstanceRecordValue> record) {
    final var writer =
        ExporterBatchWriter.Builder.begin(new CamundaExporterMetrics(new SimpleMeterRegistry()))
            .withHandler(handler)
            .build();
    writer.addRecord(record);
    try {
      writer.flush(newElasticsearchLikeBatchRequest());
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * A BatchRequest whose {@code upsert} applies Elasticsearch partial-{@code doc} merge semantics:
   * only the keys present in {@code updateFields} are written; every other field of the stored
   * document is left as it was.
   */
  private BatchRequest newElasticsearchLikeBatchRequest() {
    final BatchRequest batchRequest = mock(BatchRequest.class);
    when(batchRequest.upsert(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              final String id = invocation.getArgument(1);
              final Map<String, Object> updateFields = invocation.getArgument(3);
              elasticsearch.computeIfAbsent(id, k -> new HashMap<>()).putAll(updateFields);
              return batchRequest;
            });
    return batchRequest;
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
