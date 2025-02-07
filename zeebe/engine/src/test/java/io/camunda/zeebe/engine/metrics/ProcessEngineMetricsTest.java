/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ProcessEngineMetricsTest {

  private static final String DMN_RESOURCE = "/dmn/drg-force-user-with-assertions.dmn";
  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldIncreaseSuccessfullyEvaluatedDmnElements() {
    engine
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .businessRuleTask(
                    TASK_ID,
                    t -> t.zeebeCalledDecisionId("force_user").zeebeResultVariable("result"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("lightsaberColor", "blue", "height", 175))
            .create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    assertThat(activatedProcessInstanceMetric()).isNotNull().isOne();
    assertThat(completedProcessInstanceMetric()).isNotNull().isOne();

    assertThat(succeededEvaluatedDmnElementsMetric())
        .isNotNull()
        .describedAs(
            "Expected two decision where executed, i.e. the root decision and one required decision")
        .isEqualTo(2);
  }

  @Test
  public void shouldIncreaseFailedEvaluatedDmnElements() {
    engine
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .businessRuleTask(
                    TASK_ID,
                    t -> t.zeebeCalledDecisionId("force_user").zeebeResultVariable("result"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("lightsaberColor", "blue")
            .create();

    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    engine.processInstance().withInstanceKey(processInstanceKey).cancel();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    assertThat(activatedProcessInstanceMetric()).isNotNull().isOne();
    assertThat(terminatedProcessInstanceMetric()).isNotNull().isOne();

    assertThat(failedEvaluatedDmnElementsMetric())
        .isNotNull()
        .describedAs(
            "Expected two decision where executed as the required decision succeeded but the root decision failed")
        .isEqualTo(2);
  }

  @Test
  public void shouldIncreaseFailedEvaluatedDmnElementsIfRequiredDecisionFailed() {
    engine
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .businessRuleTask(
                    TASK_ID,
                    t -> t.zeebeCalledDecisionId("force_user").zeebeResultVariable("result"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    assertThat(failedEvaluatedDmnElementsMetric())
        .isNotNull()
        .describedAs("Expected only one decision was executed as the required decision failed")
        .isOne();
  }

  @Test
  public void shouldIncreaseProcessInstanceCreatedAtDefaultStartEvent() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent("start").endEvent("end").done())
        .deploy();

    // when
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThatCode(() -> processInstanceCreationsMetric("creation_at_given_element"))
        .isInstanceOf(MeterNotFoundException.class);
    assertThat(processInstanceCreationsMetric("creation_at_default_start_event")).isOne();
  }

  @Test
  public void shouldIncreaseProcessInstanceCreatedAtGivenElement() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent("start").endEvent("end").done())
        .deploy();

    // when
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).withStartInstruction("end").create();

    // then
    assertThatCode(() -> processInstanceCreationsMetric("creation_at_default_start_event"))
        .isInstanceOf(MeterNotFoundException.class);
    assertThat(processInstanceCreationsMetric("creation_at_given_element")).isOne();
  }

  private Double activatedProcessInstanceMetric() {
    return executedProcessInstanceMetric("activated");
  }

  private Double completedProcessInstanceMetric() {
    return executedProcessInstanceMetric("completed");
  }

  private Double terminatedProcessInstanceMetric() {
    return executedProcessInstanceMetric("terminated");
  }

  private Double executedProcessInstanceMetric(final String action) {
    return engine
        .getMeterRegistry()
        .get("zeebe.executed.instances.total")
        .tag("organizationId", "null")
        .tag("type", "ROOT_PROCESS_INSTANCE")
        .tag("action", action)
        .tag("partition", "1")
        .counter()
        .count();
  }

  private Double processInstanceCreationsMetric(final String creationMode) {
    return engine
        .getMeterRegistry()
        .get("zeebe.process.instance.creations.total")
        .tag("partition", "1")
        .tag("creation_mode", creationMode)
        .counter()
        .count();
  }

  private Double succeededEvaluatedDmnElementsMetric() {
    return evaluatedDmnElementsMetric("evaluated_successfully");
  }

  private Double failedEvaluatedDmnElementsMetric() {
    return evaluatedDmnElementsMetric("evaluated_failed");
  }

  private Double evaluatedDmnElementsMetric(final String action) {
    return engine
        .getMeterRegistry()
        .get("zeebe.evaluated.dmn.elements.total")
        .tag("organizationId", "null")
        .tag("action", action)
        .tag("partition", "1")
        .counter()
        .count();
  }
}
