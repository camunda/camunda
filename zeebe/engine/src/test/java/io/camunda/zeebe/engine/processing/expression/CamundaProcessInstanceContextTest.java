/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CamundaProcessInstanceContextTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldResolveProcessInstanceKeyInInputMapping() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                b ->
                    b.zeebeJobType("type")
                        .zeebeInputExpression("camunda.processInstance.key", "pikVar"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.jobs().withType("type").activate();

    // then
    final var job =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
            .withType("type")
            .getFirst()
            .getValue()
            .getJobs()
            .get(0);

    assertThat(job.getVariables()).containsEntry("pikVar", processInstanceKey);
  }

  @Test
  public void shouldResolveProcessInstanceKeyInSubProcessInputMapping() {
    // given
    final var process =
        Bpmn.createExecutableProcess("processWithSub")
            .startEvent()
            .subProcess(
                "sub",
                s -> {
                  s.embeddedSubProcess()
                      .startEvent()
                      .serviceTask("inner", b -> b.zeebeJobType("innerType"))
                      .endEvent();
                  s.zeebeInputExpression("camunda.processInstance.key", "pikVar");
                })
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("processWithSub").create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.jobs().withType("innerType").activate();

    // then
    final var job =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
            .withType("innerType")
            .getFirst()
            .getValue()
            .getJobs()
            .get(0);

    assertThat(job.getVariables()).containsEntry("pikVar", processInstanceKey);
  }

  @Test
  public void shouldResolveProcessInstanceKeyInExpressionWithOtherFeel() {
    // given
    final var process =
        Bpmn.createExecutableProcess("processConcat")
            .startEvent()
            .serviceTask(
                "task",
                b ->
                    b.zeebeJobType("type")
                        .zeebeInputExpression(
                            "\"id-\" + string(camunda.processInstance.key)", "ref"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("processConcat").create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.jobs().withType("type").activate();

    // then
    final var job =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
            .withType("type")
            .getFirst()
            .getValue()
            .getJobs()
            .get(0);

    assertThat(job.getVariables()).containsEntry("ref", "id-" + processInstanceKey);
  }

  @Test
  public void shouldResolveProcessInstanceBusinessIdInInputMapping() {
    // given
    final var process =
        Bpmn.createExecutableProcess("processWithBid")
            .startEvent()
            .serviceTask(
                "task",
                b ->
                    b.zeebeJobType("bidType")
                        .zeebeInputExpression("camunda.processInstance.businessId", "bidVar"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("processWithBid")
            .withBusinessId("ORDER-42")
            .create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.jobs().withType("bidType").activate();

    // then
    final var job =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
            .withType("bidType")
            .getFirst()
            .getValue()
            .getJobs()
            .get(0);

    assertThat(job.getVariables()).containsEntry("bidVar", "ORDER-42");
  }

  @Test
  public void shouldResolveProcessInstanceBusinessIdAsNullWhenNotSet() {
    // given - process started without an explicit businessId
    final var process =
        Bpmn.createExecutableProcess("processWithoutBid")
            .startEvent()
            .serviceTask(
                "task",
                b ->
                    b.zeebeJobType("noBidType")
                        .zeebeInputExpression("camunda.processInstance.businessId", "bidVar"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("processWithoutBid").create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.jobs().withType("noBidType").activate();

    // then - unset businessId resolves to null so users can detect absence
    final var job =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
            .withType("noBidType")
            .getFirst()
            .getValue()
            .getJobs()
            .get(0);

    assertThat(job.getVariables()).containsEntry("bidVar", null);
  }

  @Test
  public void shouldResolveProcessInstanceBusinessIdInSubProcessInputMapping() {
    // given
    final var process =
        Bpmn.createExecutableProcess("processBidSub")
            .startEvent()
            .subProcess(
                "sub",
                s -> {
                  s.embeddedSubProcess()
                      .startEvent()
                      .serviceTask("inner", b -> b.zeebeJobType("innerBidType"))
                      .endEvent();
                  s.zeebeInputExpression("camunda.processInstance.businessId + \"-child\"", "ref");
                })
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("processBidSub").withBusinessId("PARENT").create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.jobs().withType("innerBidType").activate();

    // then
    final var job =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
            .withType("innerBidType")
            .getFirst()
            .getValue()
            .getJobs()
            .get(0);

    assertThat(job.getVariables()).containsEntry("ref", "PARENT-child");
  }

  @Test
  public void shouldResolveProcessInstanceKeyInOutputMapping() {
    // given - service task with output mapping using camunda.processInstance.key
    final var process =
        Bpmn.createExecutableProcess("processOut")
            .startEvent()
            .serviceTask(
                "task",
                b ->
                    b.zeebeJobType("outType")
                        .zeebeOutputExpression("camunda.processInstance.key", "outVar"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("processOut").create();

    // when - the job completes, triggering the output mapping
    ENGINE.job().withType("outType").ofInstance(processInstanceKey).complete();

    // then - the namespace is now available in all FEEL expressions, so the
    // output mapping resolves to the actual processInstanceKey
    final var variableRecord =
        RecordingExporter.variableRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withName("outVar")
            .getFirst()
            .getValue();

    assertThat(variableRecord.getValue()).isEqualTo(String.valueOf(processInstanceKey));
  }

  @Test
  public void shouldResolveProcessInstanceKeyInJobTypeExpression() {
    // given - job type expression using camunda.processInstance.key
    final var process =
        Bpmn.createExecutableProcess("processJobType")
            .startEvent()
            .serviceTask(
                "task", b -> b.zeebeJobTypeExpression("string(camunda.processInstance.key)"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("processJobType").create();

    // then - the namespace is now available in all FEEL expressions, so the job
    // type expression resolves to the string form of the processInstanceKey
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    assertThat(job.getType()).isEqualTo(String.valueOf(processInstanceKey));
  }

  @Test
  public void shouldResolveProcessInstanceKeyInSequenceFlowCondition() {
    // given - a sequence-flow condition referencing camunda.processInstance.key
    final var process =
        Bpmn.createExecutableProcess("processCondition")
            .startEvent()
            .exclusiveGateway("gateway")
            .conditionExpression("=camunda.processInstance.key > 0")
            .serviceTask("matchedTask", b -> b.zeebeJobType("matched"))
            .endEvent()
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .endEvent("noEnd")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("processCondition").create();

    // then - the condition resolves truthy because the namespace is available,
    // so the "matched" job is created on the truthy branch
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("matched")
            .getFirst()
            .getValue();

    assertThat(job.getType()).isEqualTo("matched");
  }
}
