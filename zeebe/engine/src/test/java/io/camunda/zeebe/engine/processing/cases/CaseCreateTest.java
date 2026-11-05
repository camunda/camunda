/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.cases;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.cases.CaseRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.CaseIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CaseCreateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateCaseForExistingProcessInstance() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().userTask().endEvent().done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.writeCommandOnPartition(
        1,
        -1,
        CaseIntent.CREATE,
        new CaseRecord().setProcessInstanceKey(processInstanceKey).setTitle("my case"));

    // then
    final Record<CaseRecordValue> created =
        RecordingExporter.caseRecords(CaseIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(created.getKey()).isPositive().isNotEqualTo(processInstanceKey);
    assertThat(created.getValue().getTitle()).isEqualTo("my case");
  }

  @Test
  public void shouldRejectCreateForNonExistingProcessInstance() {
    // given
    final long unknownProcessInstanceKey = 123_456_789L;

    // when
    ENGINE.writeCommandOnPartition(
        1,
        -1,
        CaseIntent.CREATE,
        new CaseRecord().setProcessInstanceKey(unknownProcessInstanceKey).setTitle("my case"));

    // then
    final var rejection =
        RecordingExporter.caseRecords(CaseIntent.CREATE)
            .onlyCommandRejections()
            .withProcessInstanceKey(unknownProcessInstanceKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to create case for process instance with key '123456789', but no such process instance was found");
  }

  @Test
  public void shouldRejectCreateForElementInstanceThatIsNotAProcessInstance() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().userTask().endEvent().done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long userTaskElementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst()
            .getKey();

    // when
    ENGINE.writeCommandOnPartition(
        1,
        -1,
        CaseIntent.CREATE,
        new CaseRecord().setProcessInstanceKey(userTaskElementInstanceKey).setTitle("my case"));

    // then
    final var rejection =
        RecordingExporter.caseRecords(CaseIntent.CREATE)
            .onlyCommandRejections()
            .withProcessInstanceKey(userTaskElementInstanceKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }
}
