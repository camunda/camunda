/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordStream;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class UpdateVariableDocumentTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldProduceCorrectSequenceOfEvents() {
    // given
    final String processId = "process";
    final String taskId = "task";
    final String type = UUID.randomUUID().toString();
    final BpmnModelInstance process = newProcess(processId, taskId, type);
    final Map<String, Object> document = Maps.of(entry("x", 2), entry("foo", "bar"));

    // when
    ENGINE_RULE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(processId).withVariables("{'x': 1}").create();
    final Record<ProcessInstanceRecordValue> activatedEvent = waitForActivityActivatedEvent();
    ENGINE_RULE
        .variables()
        .ofScope(activatedEvent.getKey())
        .withDocument(document)
        .withUpdateSemantic(VariableDocumentUpdateSemantic.PROPAGATE)
        .update();
    ENGINE_RULE.job().ofInstance(processInstanceKey).withType(type).complete();

    // then
    final long completedPosition =
        getProcessInstanceCompletedPosition(processId, processInstanceKey);
    final Supplier<RecordStream> recordsSupplier =
        () -> RecordingExporter.records().between(activatedEvent.getPosition(), completedPosition);

    assertVariableRecordsProduced(processInstanceKey, recordsSupplier);
    assertVariableDocumentEventProduced(document, activatedEvent, recordsSupplier);
  }

  private void assertVariableDocumentEventProduced(
      final Map<String, Object> document,
      final Record<ProcessInstanceRecordValue> activatedEvent,
      final Supplier<RecordStream> records) {
    assertThat(
            records
                .get()
                .variableDocumentRecords()
                .withIntent(VariableDocumentIntent.UPDATED)
                .withScopeKey(activatedEvent.getKey())
                .withUpdateSemantics(VariableDocumentUpdateSemantic.PROPAGATE)
                .withVariables(document)
                .getFirst())
        .isNotNull();

    final Record<VariableDocumentRecordValue> updatedRecord =
        records
            .get()
            .variableDocumentRecords()
            .withIntent(VariableDocumentIntent.UPDATED)
            .getFirst();
    assertThat(updatedRecord.getKey()).isGreaterThan(0);
  }

  private void assertVariableRecordsProduced(
      final long processInstanceKey, final Supplier<RecordStream> records) {
    assertThat(records.get().variableRecords().withProcessInstanceKey(processInstanceKey))
        .hasSize(2)
        .extracting(
            r ->
                tuple(
                    r.getIntent(),
                    r.getValue().getScopeKey(),
                    r.getValue().getName(),
                    r.getValue().getValue()))
        .contains(
            tuple(VariableIntent.CREATED, processInstanceKey, "foo", "\"bar\""),
            tuple(VariableIntent.UPDATED, processInstanceKey, "x", "2"));
  }

  private BpmnModelInstance newProcess(
      final String processId, final String taskId, final String type) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .serviceTask(taskId, b -> b.zeebeJobType(type))
        .endEvent()
        .done();
  }

  private long getProcessInstanceCompletedPosition(
      final String processId, final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(processId)
        .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .getFirst()
        .getPosition();
  }

  private Record<ProcessInstanceRecordValue> waitForActivityActivatedEvent() {
    return RecordingExporter.processInstanceRecords()
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .getFirst();
  }
}
