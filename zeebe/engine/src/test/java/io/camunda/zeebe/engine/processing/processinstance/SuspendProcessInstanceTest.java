/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.SUSPENDED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public final class SuspendProcessInstanceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldSuspendProcessInstance() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().userTask().endEvent().done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    final Record<ProcessInstanceRecordValue> suspended =
        ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // then
    assertThat(suspended.getIntent()).isEqualTo(SUSPENDED);
    Assertions.assertThat(suspended.getValue())
        .hasBpmnProcessId(processId)
        .hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldRejectSuspendIfAlreadySuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().userTask().endEvent().done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<ProcessInstanceRecordValue> rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .expectSuspendRejection()
            .suspend();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(ProcessInstanceIntent.SUSPEND)
        .hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectSuspendIfProcessInstanceNotFound() {
    // given
    final long nonExistentKey = -1L;

    // when
    final Record<ProcessInstanceRecordValue> rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(nonExistentKey)
            .onPartition(1)
            .expectSuspendRejection()
            .suspend();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(ProcessInstanceIntent.SUSPEND)
        .hasRejectionType(RejectionType.NOT_FOUND);
  }
}
