/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ModifyProcessInstanceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldWriteModifiedEventForProcessInstance() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var event =
        ENGINE.processInstance().withInstanceKey(processInstanceKey).modification().modify();

    // then
    assertThat(event)
        .hasKey(processInstanceKey)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(ProcessInstanceModificationIntent.MODIFIED);

    assertThat(event.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasNoActivateInstructions()
        .hasNoTerminateInstructions();
  }

  @Test
  public void shouldActivateRootElement() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .serviceTask("B", b -> b.zeebeJobType("B"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var processInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .modify();

    // then
    final var elementInstanceEvents =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withElementId("B")
            .withProcessInstanceKey(processInstanceKey)
            .limit("B", ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .toList();

    Assertions.assertThat(elementInstanceEvents)
        .extracting(Record::getIntent)
        .describedAs("Expect the element instance to have been activated")
        .containsExactly(
            ProcessInstanceIntent.ELEMENT_ACTIVATING, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    Assertions.assertThat(elementInstanceEvents)
        .extracting(Record::getKey)
        .describedAs("Expect each element instance event to refer to the same entity")
        .containsOnly(elementInstanceEvents.get(0).getKey());

    Assertions.assertThat(elementInstanceEvents)
        .extracting(Record::getValue)
        .describedAs("Expect each element instance event to contain the complete record value")
        .extracting(
            ProcessInstanceRecordValue::getBpmnProcessId,
            ProcessInstanceRecordValue::getProcessDefinitionKey,
            ProcessInstanceRecordValue::getProcessInstanceKey,
            ProcessInstanceRecordValue::getBpmnElementType,
            ProcessInstanceRecordValue::getElementId,
            ProcessInstanceRecordValue::getFlowScopeKey,
            ProcessInstanceRecordValue::getVersion,
            ProcessInstanceRecordValue::getParentProcessInstanceKey,
            ProcessInstanceRecordValue::getParentElementInstanceKey)
        .containsOnly(
            Tuple.tuple(
                PROCESS_ID,
                processInstance.getProcessDefinitionKey(),
                processInstanceKey,
                BpmnElementType.SERVICE_TASK,
                "B",
                processInstanceKey,
                processInstance.getVersion(),
                -1L,
                -1L));
  }
}
