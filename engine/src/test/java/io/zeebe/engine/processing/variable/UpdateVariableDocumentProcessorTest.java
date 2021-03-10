/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.variable;

import static io.zeebe.protocol.record.Assertions.assertThat;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.zeebe.protocol.record.intent.VariableIntent;
import io.zeebe.protocol.record.value.JobBatchRecordValue;
import io.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.zeebe.protocol.record.value.VariableRecordValue;
import io.zeebe.test.util.Strings;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class UpdateVariableDocumentProcessorTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String jobType;

  @Before
  public void before() {
    jobType = Strings.newRandomValidBpmnId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType(jobType))
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();
  }

  @Test
  public void shouldRejectIfNoScopeFound() {
    // given
    final long invalidScopeKey = Long.MAX_VALUE;
    final Map<String, Object> document = Map.of("foo", "bar", "baz", 1);

    // when
    final Record<VariableDocumentRecordValue> result =
        ENGINE
            .variables()
            .ofScope(invalidScopeKey)
            .withDocument(document)
            .expectRejection()
            .update();

    // then
    assertThat(result)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectOnMsgpackReadError() {
    // given
    final MutableDirectBuffer badDocument = new UnsafeBuffer(asMsgPack("{\"a\": 1}"));
    badDocument.putByte(1, (byte) 0); // overwrite string header type
    final long processInstanceKey = startProcessWithVariables(Map.of());

    // when
    final Record<VariableDocumentRecordValue> result =
        ENGINE
            .variables()
            .ofScope(processInstanceKey)
            .withDocument(badDocument)
            .expectRejection()
            .update();

    // then
    assertThat(result)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldPropagateValueFromTaskToProcess() {
    // given
    final Map<String, Object> document = Map.of("updated", "newValue", "created", 1);
    final long processInstanceKey = startProcessWithVariables(Map.of("updated", "oldValue"));
    final long serviceTaskScopeKey = getServiceTaskScopeKey();

    // when
    final Record<VariableDocumentRecordValue> result =
        ENGINE
            .variables()
            .ofScope(serviceTaskScopeKey)
            .withDocument(document)
            .withUpdateSemantic(VariableDocumentUpdateSemantic.PROPAGATE)
            .update();

    // then
    final Record<VariableRecordValue> createdVariable =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .skipUntil(r -> r.getPosition() > result.getSourceRecordPosition())
            .withScopeKey(processInstanceKey)
            .withProcessInstanceKey(processInstanceKey)
            .limit(document.size())
            .findFirst()
            .orElseThrow();
    final Record<VariableRecordValue> updatedVariable =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withScopeKey(processInstanceKey)
            .withProcessInstanceKey(processInstanceKey)
            .limit(document.size())
            .findFirst()
            .orElseThrow();
    assertThat(result).hasRecordType(RecordType.EVENT).hasIntent(VariableDocumentIntent.UPDATED);
    assertThat(createdVariable.getValue()).hasName("created").hasValue("1");
    assertThat(updatedVariable.getValue()).hasName("updated").hasValue("\"newValue\"");
  }

  @Test
  public void shouldNotPropagateValueWithLocalSemantic() {
    // given
    final Map<String, Object> document = Map.of("updated", "newValue");
    final long processInstanceKey = startProcessWithVariables(Map.of("updated", "oldValue"));
    final long serviceTaskScopeKey = getServiceTaskScopeKey();

    // when
    final Record<VariableDocumentRecordValue> result =
        ENGINE
            .variables()
            .ofScope(serviceTaskScopeKey)
            .withDocument(document)
            .withUpdateSemantic(VariableDocumentUpdateSemantic.LOCAL)
            .update();

    // then
    final Record<VariableRecordValue> createdVariable =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .skipUntil(r -> r.getPosition() > result.getSourceRecordPosition())
            .withScopeKey(serviceTaskScopeKey)
            .withProcessInstanceKey(processInstanceKey)
            .limit(document.size())
            .findFirst()
            .orElseThrow();
    assertThat(result).hasRecordType(RecordType.EVENT).hasIntent(VariableDocumentIntent.UPDATED);
    assertThat(createdVariable.getValue()).hasName("updated").hasValue("\"newValue\"");
  }

  @Test
  public void shouldNotPropagateExistingVariable() {
    // given
    final Map<String, Object> document = Map.of("updated", "newValue");
    final long processInstanceKey = startProcessWithVariables(Map.of());
    final long serviceTaskScopeKey = getServiceTaskScopeKey();

    // when
    ENGINE
        .variables()
        .ofScope(serviceTaskScopeKey)
        .withDocument(Map.of("updated", "oldValue"))
        .withUpdateSemantic(VariableDocumentUpdateSemantic.LOCAL)
        .update();
    final Record<VariableDocumentRecordValue> result =
        ENGINE
            .variables()
            .ofScope(serviceTaskScopeKey)
            .withDocument(document)
            .withUpdateSemantic(VariableDocumentUpdateSemantic.PROPAGATE)
            .update();

    // then
    final Record<VariableRecordValue> updatedVariable =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withScopeKey(serviceTaskScopeKey)
            .withProcessInstanceKey(processInstanceKey)
            .limit(document.size())
            .findFirst()
            .orElseThrow();
    assertThat(result).hasRecordType(RecordType.EVENT).hasIntent(VariableDocumentIntent.UPDATED);
    assertThat(updatedVariable.getValue()).hasName("updated").hasValue("\"newValue\"");
  }

  private long startProcessWithVariables(final Map<String, Object> variables) {
    return ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariables(variables).create();
  }

  private long getServiceTaskScopeKey() {
    final Record<JobBatchRecordValue> activatedJobs =
        Awaitility.await()
            .until(
                () -> ENGINE.jobs().withType(jobType).activate(),
                r -> r.getValue().getJobs().size() > 0);
    return activatedJobs.getValue().getJobs().get(0).getElementInstanceKey();
  }
}
