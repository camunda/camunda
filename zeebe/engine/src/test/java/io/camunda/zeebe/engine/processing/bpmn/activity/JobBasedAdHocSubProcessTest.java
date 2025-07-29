/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AdHocSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAdHocImplementationType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class JobBasedAdHocSubProcessTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  public static final String JOB_TYPE = "jobType";
  private static final String PROCESS_ID = "process";
  private static final String AHSP_ELEMENT_ID = "ad-hoc";
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  private BpmnModelInstance process(final Consumer<AdHocSubProcessBuilder> modifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .adHocSubProcess(AHSP_ELEMENT_ID, modifier)
        .zeebeImplementation(ZeebeAdHocImplementationType.JOB_WORKER)
        .zeebeJobType(JOB_TYPE)
        .endEvent()
        .done();
  }

  @Test
  public void shouldDeployProcess() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A1").task("A2");
              adHocSubProcess.task("B");
            });

    // when
    final Record<DeploymentRecordValue> deploymentEvent =
        ENGINE.deployment().withXmlResource(process).deploy();

    // then
    assertThat(deploymentEvent).hasRecordType(RecordType.EVENT).hasIntent(DeploymentIntent.CREATED);
  }

  @Test
  public void shouldActivateAdHocSubProcess() {
    // given
    final BpmnModelInstance process = process(adHocSubProcess -> adHocSubProcess.task("A"));

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(AHSP_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSequence(
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .getFirst()
                .getValue())
        .hasElementId(AHSP_ELEMENT_ID)
        .hasBpmnElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
        .hasBpmnEventType(BpmnEventType.UNSPECIFIED)
        .hasFlowScopeKey(processInstanceKey);
  }

  @Test
  public void shouldCreateJobOnActivation() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A1");
            });
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final var adHocSubProcess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .withElementId(AHSP_ELEMENT_ID)
            .getFirst();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .getFirst()
                .getValue())
        .hasType(JOB_TYPE)
        .hasRetries(3)
        .hasElementInstanceKey(adHocSubProcess.getKey())
        .hasElementId(adHocSubProcess.getValue().getElementId())
        .hasProcessDefinitionKey(adHocSubProcess.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(adHocSubProcess.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(adHocSubProcess.getValue().getVersion());
  }

  @Test
  public void shouldCancelJobOnTermination() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A1");
            });
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    final var adHocSubProcess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .withElementId(AHSP_ELEMENT_ID)
            .getFirst();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .getFirst()
                .getValue())
        .hasType(JOB_TYPE)
        .hasRetries(3)
        .hasElementInstanceKey(adHocSubProcess.getKey())
        .hasElementId(adHocSubProcess.getValue().getElementId())
        .hasProcessDefinitionKey(adHocSubProcess.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(adHocSubProcess.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(adHocSubProcess.getValue().getVersion());
  }
}
