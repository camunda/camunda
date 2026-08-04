/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.cache.TestProcessCache;
import io.camunda.exporter.handlers.FlowNodeInstanceFromProcessInstanceHandler;
import io.camunda.exporter.handlers.FlowNodeInstanceNameFromAdHocActivityHandler;
import io.camunda.exporter.store.ExporterBatchWriter.Builder;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins the shared-mutable-entity mechanism behind #58803, in isolation from a broker: {@link
 * ExporterBatchWriter} caches one entity per {@code (id, entityType)} across all handlers that
 * touch it within a single flush batch. {@link FlowNodeInstanceNameFromAdHocActivityHandler} and
 * {@link FlowNodeInstanceFromProcessInstanceHandler} both target the ad-hoc inner instance's own id
 * — the former via the entry child's {@code ELEMENT_ACTIVATING} record (its {@code flowScopeKey}),
 * the latter via the inner instance's own lifecycle records (its {@code key}).
 *
 * <p>If the inner instance's own completion record is processed after the entry child's activation
 * within the same batch, {@code FlowNodeInstanceFromProcessInstanceHandler#updateEntity} clobbers
 * the shared entity's {@code flowNodeName} back to {@code null} — that synthetic id never resolves
 * in the BPMN — right before {@code FlowNodeInstanceNameFromAdHocActivityHandler#flush}'s {@code
 * Map.of(FLOW_NODE_NAME, entity.getFlowNodeName())} runs on it. This must not throw a {@link
 * NullPointerException} (#58803).
 */
public class ExporterBatchWriterFlowNodeInstanceNameClobberTest {

  private static final long PROCESS_DEFINITION_KEY = 222L;
  private static final long INNER_INSTANCE_KEY = 999L;
  private static final String ENTRY_ELEMENT_ID = "listUsers";
  private static final String INNER_INSTANCE_ELEMENT_ID = "adHocSubProcess#innerInstance";

  private final ProtocolFactory factory = new ProtocolFactory();
  private final TestProcessCache processCache = new TestProcessCache();

  @Test
  public void shouldNotThrowWhenInnerInstanceCompletionClobbersNameInSameBatch() {
    // given
    processCache.put(
        PROCESS_DEFINITION_KEY,
        new CachedProcessEntity(
            "process",
            1,
            null,
            List.of(),
            Map.of(ENTRY_ELEMENT_ID, "List users"),
            false,
            Map.of(),
            Set.of(ENTRY_ELEMENT_ID)));
    final var writer =
        Builder.begin()
            .withHandler(
                new FlowNodeInstanceNameFromAdHocActivityHandler(
                    "flow-node-instance", processCache))
            .withHandler(
                new FlowNodeInstanceFromProcessInstanceHandler("flow-node-instance", processCache))
            .build();

    // the entry child's activation resolves the name and caches it onto the inner instance entity
    writer.addRecord(entryElementActivatingRecord());
    // the inner instance's own completion, processed in the same batch, clobbers it back to null
    writer.addRecord(innerInstanceCompletedRecord());
    final var batchRequest = mock(BatchRequest.class);

    // when - then
    assertThatCode(() -> writer.flush(batchRequest)).doesNotThrowAnyException();
    // the resolved name must reach the batch request, not just avoid the NPE
    verify(batchRequest)
        .upsertWithScript(
            any(),
            eq(String.valueOf(INNER_INSTANCE_KEY)),
            any(),
            eq(FlowNodeInstanceNameFromAdHocActivityHandler.SET_IF_NULL_NAME_SCRIPT),
            eq(Map.of(FlowNodeInstanceTemplate.FLOW_NODE_NAME, "List users")));
  }

  private Record<ProcessInstanceRecordValue> entryElementActivatingRecord() {
    final ProcessInstanceRecordValue value =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withElementId(ENTRY_ELEMENT_ID)
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withFlowScopeKey(INNER_INSTANCE_KEY)
            .build();
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        r -> r.withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING).withValue(value));
  }

  private Record<ProcessInstanceRecordValue> innerInstanceCompletedRecord() {
    final ProcessInstanceRecordValue value =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withElementId(INNER_INSTANCE_ELEMENT_ID)
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withBpmnElementType(BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE)
            .build();
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        r ->
            r.withKey(INNER_INSTANCE_KEY)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withValue(value));
  }
}
