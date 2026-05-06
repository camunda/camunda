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
}
