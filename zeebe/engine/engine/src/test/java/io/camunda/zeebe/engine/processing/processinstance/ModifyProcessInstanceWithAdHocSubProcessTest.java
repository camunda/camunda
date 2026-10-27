/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AdHocSubProcessBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ModifyProcessInstanceWithAdHocSubProcessTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String AD_HOC_SUB_PROCESS_ELEMENT_ID = "ad-hoc";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  private BpmnModelInstance process(final java.util.function.Consumer<AdHocSubProcessBuilder> mod) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .adHocSubProcess(AD_HOC_SUB_PROCESS_ELEMENT_ID, mod)
        .endEvent()
        .done();
  }

  @Test
  public void shouldActivateElementsInsideAdHocSubProcessViaModification() {
    // given
    final BpmnModelInstance model =
        process(
            adHoc -> {
              adHoc.task("A");
              adHoc.task("B");
            });

    ENGINE.deployment().withXmlResource(model).deploy();

    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // wait until ad-hoc sub-process is active
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(piKey)
        .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
        .await();

    // when
    final var modified =
        ENGINE
            .processInstance()
            .withInstanceKey(piKey)
            .modification()
            .activateElement("A")
            .activateElement("B")
            .modify();

    // then
    assertThat(modified.getIntent())
        .describedAs("Expect that modification was successful")
        .isEqualTo(ProcessInstanceModificationIntent.MODIFIED);

    // verify elements A and B got activated as a result of modification
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(piKey)
                .filter(
                    r ->
                        (r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED)
                            && ("A".equals(r.getValue().getElementId())
                                || "B".equals(r.getValue().getElementId())))
                .limit(2))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsExactlyInAnyOrder(
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("B", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldTerminateElementInstancesInsideAdHocSubProcessViaModification() {
    // given
    final BpmnModelInstance model =
        process(adHoc -> adHoc.serviceTask("ServiceTask", b -> b.zeebeJobType("test")));

    ENGINE.deployment().withXmlResource(model).deploy();

    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // wait until ad-hoc sub-process is active
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(piKey)
        .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
        .await();

    // activate the service task via modification to obtain an element instance to terminate
    ENGINE
        .processInstance()
        .withInstanceKey(piKey)
        .modification()
        .activateElement("ServiceTask")
        .modify();

    final var serviceTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(piKey)
            .withElementId("ServiceTask")
            .getFirst();

    // when
    final var terminated =
        ENGINE
            .processInstance()
            .withInstanceKey(piKey)
            .modification()
            .terminateElement(serviceTaskInstance.getKey())
            .modify();

    // then
    assertThat(terminated.getIntent())
        .describedAs("Expect that termination via modification was successful")
        .isEqualTo(ProcessInstanceModificationIntent.MODIFIED);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(piKey)
                .withElementId("ServiceTask")
                .exists())
        .isTrue();
  }
}
