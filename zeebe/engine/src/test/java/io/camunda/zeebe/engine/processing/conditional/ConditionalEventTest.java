/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.conditional;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ConditionSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ConditionalEventTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldCreateConditionSubscription() {
    // given
    final String processId = helper.getBpmnProcessId();

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .conditionalEventDefinition("conditional")
                    .condition("x > 1")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .deploy();

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).await();
  }

  @Test
  public void shouldCreateConditionSubscriptionForXML() {
    // given
    engine.deployment().withXmlClasspathResource("/processes/conditional.bpmn").deploy();

    engine
        .processInstance()
        .ofBpmnProcessId("testProcessId")
        .withVariables(Map.of("x", 2))
        .create();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).exists())
        .isTrue();

    assertThat(
            RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGERED).exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerConditionalEventOnBoundaryEventActivation() {
    // given
    final String processId = helper.getBpmnProcessId();

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("A")
                .boundaryEvent("boundary1")
                .conditionalEventDefinition("conditional")
                .condition("x > 1")
                .conditionalEventDefinitionDone()
                .endEvent()
                .moveToActivity("A")
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariables(Map.of("x", 2)).create();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).exists())
        .isTrue();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGER).exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary1")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldNotTriggerConditionalEventOnBoundaryEventActivationWhenConditionIsNotMet() {
    // given
    final String processId = helper.getBpmnProcessId();

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .conditionalEventDefinition("conditional")
                    .condition("x > 1")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .deploy();

    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariables(Map.of("x", 1)).create();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).exists())
        .isTrue();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGER).exists())
        .isFalse();
  }

  @Test
  public void shouldTriggerNestedConditionalEventOnBoundaryEventActivation() {
    // given
    final String processId = helper.getBpmnProcessId();

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .manualTask()
                    .subProcess()
                    .embeddedSubProcess()
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .conditionalEventDefinition("conditional")
                    .condition("x > 1")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .subProcessDone()
                    .endEvent()
                    .done())
            .deploy();

    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariables(Map.of("x", 2)).create();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).exists())
        .isTrue();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGER).exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary1")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldNotTriggerNestedConditionalEventOnBoundaryEventActivation() {
    // given
    final String processId = helper.getBpmnProcessId();

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .manualTask()
                    .subProcess()
                    .embeddedSubProcess()
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .conditionalEventDefinition("conditional")
                    .condition("x > 1")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .subProcessDone()
                    .endEvent()
                    .done())
            .deploy();

    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariables(Map.of("x", 1)).create();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).exists())
        .isTrue();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGER).exists())
        .isFalse();
  }

  @Test
  public void shouldTriggerMultipleConditionalEventsOnBoundaryEventActivations() {
    // given
    final String processId = helper.getBpmnProcessId();

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .conditionalEventDefinition("conditional1")
                    .condition("x > 1")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .moveToActivity("A")
                    .boundaryEvent("boundary2")
                    .conditionalEventDefinition("conditional2")
                    .condition("x > 2")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .deploy();

    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariables(Map.of("x", 3)).create();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).limit(2))
        .hasSize(2);

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGER).limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary2")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();

    // first conditional event interrupts the user task, so the second conditional event cannot be
    // triggered anymore
    assertThat(
            RecordingExporter.records()
                .withIntent(ConditionSubscriptionIntent.TRIGGER)
                .onlyCommandRejections()
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary1")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldTriggerMultipleNonInterruptingConditionalEventsOnBoundaryEventActivations() {
    // given
    final String processId = helper.getBpmnProcessId();

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .cancelActivity(false)
                    .conditionalEventDefinition("conditional1")
                    .condition("x > 1")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .moveToActivity("A")
                    .boundaryEvent("boundary2")
                    .cancelActivity(false)
                    .conditionalEventDefinition("conditional2")
                    .condition("x > 2")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .deploy();

    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariables(Map.of("x", 3)).create();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).limit(2))
        .hasSize(2);

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGER).limit(2))
        .hasSize(2);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary2")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();

    // first conditional event does not interrupt the user task, so the second conditional event can
    // be
    // triggered as well
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary1")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();
  }

  // VARIABLE CHANGES TESTS

  @Test
  public void shouldTriggerConditionalEventOnBoundaryEventOnVariableCreation() {
    // given
    final String processId = helper.getBpmnProcessId();

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .conditionalEventDefinition("conditional")
                    .condition("x > 1")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .deploy();

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).exists())
        .isTrue();

    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 2)).update();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGER).exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary1")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerConditionalEventOnBoundaryEventOnVariableUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .conditionalEventDefinition("conditional")
                    .condition("x > 1")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .deploy();

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).exists())
        .isTrue();

    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 1)).update();

    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("x")
                .withValue("1")
                .exists())
        .isTrue();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGER).exists())
        .isFalse();

    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 2)).update();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGER).exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary1")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerMultipleConditionalEventsOnVariableCreation() {
    // given
    final String processId = helper.getBpmnProcessId();

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .conditionalEventDefinition("conditional1")
                    .condition("x > 1")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .moveToActivity("A")
                    .boundaryEvent("boundary2")
                    .conditionalEventDefinition("conditional2")
                    .condition("x > 2")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .deploy();

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).limit(2))
        .hasSize(2);

    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 3)).update();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGER).limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary2")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();

    // first conditional event interrupts the user task, so the second conditional event cannot be
    // triggered anymore
    assertThat(
            RecordingExporter.records()
                .withIntent(ConditionSubscriptionIntent.TRIGGER)
                .onlyCommandRejections()
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary1")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldTriggerMultipleNonInterruptingConditionalEventsOnVariableCreation() {
    // given
    final String processId = helper.getBpmnProcessId();

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .cancelActivity(false)
                    .conditionalEventDefinition("conditional1")
                    .condition("x > 1")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .moveToActivity("A")
                    .boundaryEvent("boundary2")
                    .cancelActivity(false)
                    .conditionalEventDefinition("conditional2")
                    .condition("x > 2")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .deploy();

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).limit(2))
        .hasSize(2);

    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 3)).update();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGER).limit(2))
        .hasSize(2);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary2")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();

    // first conditional event does not interrupt the user task, so the second conditional event can
    // be triggered as well
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary1")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerConditionalBoundaryEventOnlyOnceOnMultipleVariableCreation() {
    // given
    final String processId = helper.getBpmnProcessId();

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .cancelActivity(false)
                    .conditionalEventDefinition("conditional")
                    .condition("x > 1")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .deploy();

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).exists())
        .isTrue();

    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 2, "y", 1)).update();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGER))
        .hasSize(1);
  }

  // INTERMEDIATE CATCH EVENT

  @Test
  public void shouldCreateConditionSubscriptionForIntermediateCatchEvent() {
    // given
    final String processId = helper.getBpmnProcessId();

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .intermediateCatchEvent("catch1")
                    .conditionalEventDefinition("conditional")
                    .condition("x > 1")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .done())
            .deploy();

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).await();
  }

  // EVENT SUBPROCESS START EVENT

  @Test
  public void shouldCreateConditionSubscriptionForEventSubprocessStartEvent() {
    // given
    final String processId = helper.getBpmnProcessId();

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "eventSubProcess",
                        sub -> {
                          sub.startEvent("startEvent")
                              .conditionalEventDefinition("conditional")
                              .condition("x > 1")
                              .conditionalEventDefinitionDone()
                              .endEvent();
                        })
                    .startEvent()
                    .userTask("A")
                    .endEvent()
                    .done())
            .deploy();

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).await();
  }

  // EXECUTION LISTENERS

  @Test
  public void shouldTriggerConditionalEventOnAllStartELCompleted() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String startElJobType = helper.getStartExecutionListenerType();
    final String startElJobType2 = helper.getStartExecutionListenerType() + "2";
    final String serviceTaskJobType = helper.getJobType();

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType(serviceTaskJobType)
                            .zeebeStartExecutionListener(startElJobType)
                            .zeebeStartExecutionListener(startElJobType2))
                .boundaryEvent("boundary1")
                .conditionalEventDefinition("conditional")
                .condition("x > 1")
                .conditionalEventDefinitionDone()
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // complete the start execution listener job to continue the process execution
    RecordingExporter.jobRecords().withType(startElJobType).withIntent(JobIntent.CREATED).await();
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType(startElJobType)
        .withVariables(Map.of("x", 2))
        .complete();

    engine.job().ofInstance(processInstanceKey).withType(startElJobType2).complete();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).exists())
        .isTrue();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGER).exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary1")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();

    assert false;
  }

  // TODO - no unsubscribe on element completion yet, so triggers even after end el but should not
  // when unsubscribe is implemented
  @Test
  public void shouldNotTriggerConditionalEventOnEndELCompleted() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String endElJobType = helper.getEndExecutionListenerType();
    final String endElJobType2 = helper.getEndExecutionListenerType() + "2";
    final String serviceTaskJobType = helper.getJobType();

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType(serviceTaskJobType)
                            .zeebeEndExecutionListener(endElJobType)
                            .zeebeEndExecutionListener(endElJobType2))
                .boundaryEvent("boundary1")
                .conditionalEventDefinition("conditional")
                .condition("x > 1")
                .conditionalEventDefinitionDone()
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.jobRecords()
        .withType(serviceTaskJobType)
        .withIntent(JobIntent.CREATED)
        .await();
    engine.job().ofInstance(processInstanceKey).withType(serviceTaskJobType).complete();

    // complete the start execution listener job to continue the process execution
    RecordingExporter.jobRecords().withType(endElJobType).withIntent(JobIntent.CREATED).await();
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType(endElJobType)
        .withVariables(Map.of("x", 2))
        .complete();

    engine.job().ofInstance(processInstanceKey).withType(endElJobType2).complete();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).exists())
        .isTrue();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGER).exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary1")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();

    assert false;
  }

  // MULTI-INSTANCE TASKS

  @Test
  public void shouldInterruptMultiInstanceTaskOnChildInstanceMergeVariablesThatTriggersCondition() {
    // given
    final String processId = helper.getBpmnProcessId();

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        "miTask",
                        t ->
                            t.zeebeJobType("miJob")
                                .multiInstance(
                                    mi ->
                                        mi.zeebeInputCollectionExpression("items")
                                            .zeebeInputElement("item")
                                            .zeebeOutputCollection("outputs")
                                            .zeebeOutputElementExpression("output")))
                    .boundaryEvent("boundary1")
                    .conditionalEventDefinition("conditional")
                    .condition("x > 1")
                    .conditionalEventDefinitionDone()
                    .endEvent()
                    .moveToActivity("miTask")
                    .endEvent()
                    .done())
            .deploy();

    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("items", new String[] {"a", "b", "c"}))
            .create();

    // complete first child instance
    final var miJob1 =
        RecordingExporter.jobRecords()
            .withType("miJob")
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("miTask")
            .withIntent(JobIntent.CREATED)
            .getFirst();
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withKey(miJob1.getKey())
        .withVariables(Map.of("output", "a"))
        .complete();

    // complete second child instance and merge variables that trigger the condition
    final var miJob2 =
        RecordingExporter.jobRecords()
            .withType("miJob")
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("miTask")
            .withIntent(JobIntent.CREATED)
            .toList()
            .get(1);
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withKey(miJob2.getKey())
        .withVariables(Map.of("output", "b", "x", 2))
        .complete();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).exists())
        .isTrue();
    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGER).exists())
        .isTrue();
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary1")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();

    assert false;
  }

  // CONDITION SUBSCRIPTION TRIGGER WILL BE REJECTED ON SERVICE TASK COMPLETION WITH VARIABLES
  @Test
  public void shouldNotTriggerConditionalEventOnServiceTaskCompletion() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String serviceTaskJobType = helper.getJobType();

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(serviceTaskJobType))
                .boundaryEvent("boundary1")
                .conditionalEventDefinition("conditional")
                .condition("x > 1")
                .conditionalEventDefinitionDone()
                .endEvent("conditionEnd")
                .moveToActivity("task")
                .endEvent("end")
                .done())
        .deploy();

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.CREATED).exists())
        .isTrue();

    // complete the service task job with variables that trigger the condition
    RecordingExporter.jobRecords()
        .withType(serviceTaskJobType)
        .withIntent(JobIntent.CREATED)
        .await();
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType(serviceTaskJobType)
        .withVariables(Map.of("x", 2))
        .complete();

    assertThat(RecordingExporter.records().withIntent(ConditionSubscriptionIntent.TRIGGER).exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary1")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();

    assert false;
  }
}
