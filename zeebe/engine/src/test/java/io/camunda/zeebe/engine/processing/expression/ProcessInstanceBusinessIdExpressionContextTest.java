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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ProcessInstanceBusinessIdExpressionContextTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String BUSINESS_ID = "order-123";
  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldResolveBusinessIdInInputMapping() {
    // given
    final var processId = "pi-ctx-bid-input-mapping";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType("input-mapping-job")
                            .zeebeInputExpression("camunda.processInstance.businessId", "piBid"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    RecordingExporter.jobRecords(JobIntent.CREATED).withProcessInstanceKey(pi).await();
    final var job =
        ENGINE.jobs().withType("input-mapping-job").activate().getValue().getJobs().getFirst();
    assertThat(job.getVariables()).containsEntry("piBid", BUSINESS_ID);
  }

  @Test
  public void shouldResolveBusinessIdInOutputMapping() {
    // given
    final var processId = "pi-ctx-bid-output-mapping";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task1",
                    t ->
                        t.zeebeJobType("output-mapping-job-1")
                            .zeebeOutputExpression("camunda.processInstance.businessId", "piBid"))
                .serviceTask("task2", t -> t.zeebeJobType("output-mapping-job-2"))
                .endEvent()
                .done())
        .deploy();

    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // when
    ENGINE.job().ofInstance(pi).withType("output-mapping-job-1").complete();

    // then
    RecordingExporter.jobRecords(JobIntent.CREATED).withProcessInstanceKey(pi).await();
    final var job2 =
        ENGINE.jobs().withType("output-mapping-job-2").activate().getValue().getJobs().getFirst();
    assertThat(job2.getVariables()).containsEntry("piBid", BUSINESS_ID);
  }

  @Test
  public void shouldResolveBusinessIdInSequenceFlowCondition() {
    // given
    final var processId = "pi-ctx-bid-sequence-condition";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .exclusiveGateway("gateway")
                .conditionExpression("camunda.processInstance.businessId = \"order-123\"")
                .serviceTask("taken", t -> t.zeebeJobType("taken-job"))
                .endEvent()
                .moveToLastExclusiveGateway()
                .defaultFlow()
                .serviceTask("default", t -> t.zeebeJobType("default-job"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(pi)
                .withElementId("taken")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldResolveBusinessIdInJobTypeExpression() {
    // given
    final var processId = "pi-ctx-bid-job-type";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task", t -> t.zeebeJobTypeExpression("camunda.processInstance.businessId"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    assertThat(job.getType()).isEqualTo(BUSINESS_ID);
  }

  @Test
  public void shouldResolveBusinessIdInJobRetriesExpression() {
    // given
    final var processId = "pi-ctx-bid-job-retries";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType("retries-job")
                            .zeebeJobRetriesExpression(
                                "if camunda.processInstance.businessId = \"order-123\" then 3 else 1"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    assertThat(job.getRetries()).isEqualTo(3);
  }
}
