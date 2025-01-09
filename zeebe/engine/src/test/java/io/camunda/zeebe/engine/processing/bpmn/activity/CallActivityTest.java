/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.CallActivityBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.FeatureFlags;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CallActivityTest {

  public static final int CUSTOM_CALL_ACTIVITY_DEPTH = 10;
  public static final boolean ENABLE_STRAIGHT_THROUGH_PROCESSING_LOOP_DETECTOR = false;

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withEngineConfig(
              config -> config.setDefaultCallActivityMaxDepth(CUSTOM_CALL_ACTIVITY_DEPTH))
          .withFeatureFlags(
              new FeatureFlags(
                  true, true, true, true, ENABLE_STRAIGHT_THROUGH_PROCESSING_LOOP_DETECTOR, true));

  private static final String PROCESS_ID_PARENT = "wf-parent";
  private static final String PROCESS_ID_CHILD = "wf-child";

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private String jobType;

  private static BpmnModelInstance parentProcess(final Consumer<CallActivityBuilder> consumer) {
    final var builder =
        Bpmn.createExecutableProcess(PROCESS_ID_PARENT)
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId(PROCESS_ID_CHILD));

    consumer.accept(builder);

    return builder.endEvent().done();
  }

  @Before
  public void init() {
    jobType = helper.getJobType();

    final var parentProcess = parentProcess(CallActivityBuilder::done);

    final var childProcess =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD)
            .startEvent()
            .serviceTask("child-task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();

    ENGINE
        .deployment()
        .withXmlResource("wf-parent.bpmn", parentProcess)
        .withXmlResource("wf-child.bpmn", childProcess)
        .deploy();
  }

  @Test
  public void shouldActivateCallActivity() {
    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withProcessInstanceKey(processInstanceKey)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .withElementId("call")
                .limit(2))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsExactly(
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldCreateInstanceOfCalledElement() {
    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withParentProcessInstanceKey(processInstanceKey)
                .limit(6))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsExactly(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldCreateInstanceOfLatestVersionOfCalledElement() {
    // given
    final var processChildV2 =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD).startEvent("v2").endEvent().done();

    final var secondDeployment =
        ENGINE.deployment().withXmlResource("wf-child.bpmn", processChildV2).deploy();
    final var secondVersion = secondDeployment.getValue().getProcessesMetadata().get(0);

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // then
    Assertions.assertThat(getChildInstanceOf(processInstanceKey))
        .hasVersion(secondVersion.getVersion())
        .hasProcessDefinitionKey(secondVersion.getProcessDefinitionKey());
  }

  @Test
  public void shouldHaveReferenceToParentInstance() {
    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    completeJobWith(Map.of());

    // then
    final var callActivityInstanceKey = getCallActivityInstanceKey(processInstanceKey);

    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
                .processInstanceRecords()
                .withParentProcessInstanceKey(processInstanceKey))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getParentProcessInstanceKey(), v.getParentElementInstanceKey()))
        .containsOnly(tuple(processInstanceKey, callActivityInstanceKey));
  }

  @Test
  public void shouldCompleteCallActivity() {
    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    completeJobWith(Map.of());

    // then
    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
                .processInstanceRecords())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCopyVariablesToChild() {
    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID_PARENT)
            .withVariables(
                Map.of(
                    "x", 1,
                    "y", 2))
            .create();

    // then
    final var childInstance = getChildInstanceOf(processInstanceKey);

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(childInstance.getProcessInstanceKey())
                .limit(2))
        .extracting(Record::getValue)
        .allMatch(v -> v.getProcessDefinitionKey() == childInstance.getProcessDefinitionKey())
        .allMatch(v -> v.getProcessInstanceKey() == childInstance.getProcessInstanceKey())
        .allMatch(v -> v.getScopeKey() == childInstance.getProcessInstanceKey())
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .contains(tuple("x", "1"), tuple("y", "2"));
  }

  @Test
  public void shouldPropagateVariablesToParent() {
    // given
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // when
    completeJobWith(Map.of("y", 2));

    // then
    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
                .variableRecords()
                .withProcessInstanceKey(processInstanceKey))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getScopeKey(), v.getName(), v.getValue()))
        .containsExactly(tuple(processInstanceKey, "y", "2"));
  }

  @Test
  public void shouldNotPropagateVariablesToParentIfDisabled() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-parent.bpmn", parentProcess(c -> c.zeebePropagateAllChildVariables(false)))
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    final var childInstanceKey = getChildInstanceOf(processInstanceKey).getProcessInstanceKey();

    // when
    completeJobWith(Map.of("y", 2));

    // then
    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
                .variableRecords())
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getScopeKey(), v.getName()))
        .contains(tuple(childInstanceKey, "y"))
        .doesNotContain(tuple(processInstanceKey, "y"));
  }

  @Test
  public void shouldApplyInputMappings() {
    // given
    ENGINE
        .deployment()
        .withXmlResource("wf-parent.bpmn", parentProcess(c -> c.zeebeInputExpression("x", "y")))
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).withVariable("x", 1).create();

    // then
    final var childInstance = getChildInstanceOf(processInstanceKey);

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(childInstance.getProcessInstanceKey())
                .limit(2))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .contains(tuple("x", "1"), tuple("y", "1"));
  }

  @Test
  public void shouldPropagateAllVariablesWhenEnablingPropagateAllParentVariablesExplicitly() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-parent.bpmn",
            parentProcess(
                c -> c.zeebeInputExpression("x", "y").zeebePropagateAllParentVariables(true)))
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID_PARENT)
            .withVariables(Map.of("x", 1))
            .create();

    // then
    final var childInstance = getChildInstanceOf(processInstanceKey);

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(childInstance.getProcessInstanceKey())
                .limit(2))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .contains(tuple("x", "1"), tuple("y", "1"));
  }

  @Test
  public void shouldOnlyPropagateVariablesDefinedViaInputMappings() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-parent.bpmn",
            parentProcess(
                c -> c.zeebeInputExpression("x", "y").zeebePropagateAllParentVariables(false)))
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID_PARENT)
            .withVariables(Map.of("x", 1))
            .create();

    // then
    final var childInstance = getChildInstanceOf(processInstanceKey);

    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == JobIntent.CREATED)
                .variableRecords()
                .withIntent(VariableIntent.CREATED)
                .withProcessInstanceKey(childInstance.getProcessInstanceKey()))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .containsExactly(tuple("y", "1"));
  }

  @Test
  public void shouldNotPropagateAnyVariable() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-parent.bpmn", parentProcess(c -> c.zeebePropagateAllParentVariables(false)))
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID_PARENT)
            .withVariables(Map.of("x", 1))
            .create();

    // then
    final var childInstance = getChildInstanceOf(processInstanceKey);

    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == JobIntent.CREATED)
                .variableRecords()
                .withIntent(VariableIntent.CREATED)
                .withProcessInstanceKey(childInstance.getProcessInstanceKey()))
        .isEmpty();
  }

  @Test
  public void shouldApplyOutputMappings() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-parent.bpmn",
            parentProcess(
                c -> c.zeebePropagateAllChildVariables(false).zeebeOutputExpression("x", "y")))
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // when
    completeJobWith(Map.of("x", 2));

    // then
    final long callActivityInstanceKey = getCallActivityInstanceKey(processInstanceKey);

    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
                .variableRecords()
                .withProcessInstanceKey(processInstanceKey))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getScopeKey(), v.getName(), v.getValue()))
        .hasSize(2)
        .contains(tuple(callActivityInstanceKey, "x", "2"), tuple(processInstanceKey, "y", "2"));
  }

  @Test
  public void shouldApplyOutputMappingsIgnoringThePropagateAllProperty() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-parent.bpmn",
            parentProcess(
                c -> c.zeebePropagateAllChildVariables(true).zeebeOutputExpression("x", "x")))
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // when
    completeJobWith(Map.of("x", 1, "y", 2));

    // then
    final long callActivityInstanceKey = getCallActivityInstanceKey(processInstanceKey);

    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
                .variableRecords()
                .withProcessInstanceKey(processInstanceKey))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getScopeKey(), v.getName(), v.getValue()))
        .hasSize(3)
        .contains(
            tuple(callActivityInstanceKey, "x", "1"),
            tuple(callActivityInstanceKey, "y", "2"),
            tuple(processInstanceKey, "x", "1"));
  }

  @Test
  public void shouldCreateInstanceOfCalledElementWithExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-parent.bpmn",
            parentProcess(callActivity -> callActivity.zeebeProcessIdExpression("processId")))
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID_PARENT)
            .withVariable("processId", PROCESS_ID_CHILD)
            .create();

    // then
    Assertions.assertThat(getChildInstanceOf(processInstanceKey))
        .hasBpmnProcessId(PROCESS_ID_CHILD);
  }

  @Test
  public void shouldTriggerBoundaryEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "parent-wf.bpmn",
            parentProcess(
                callActivity ->
                    callActivity
                        .boundaryEvent(
                            "timeout", b -> b.cancelActivity(true).timerWithDuration("PT0.1S"))
                        .endEvent()))
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // then
    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
                .processInstanceRecords())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.TERMINATE_ELEMENT),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.TERMINATE_ELEMENT),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTerminateChildInstance() {
    // given
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    assertThat(RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).exists())
        .describedAs("Expected job in child instance to be created")
        .isTrue();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
                .processInstanceRecords())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldTerminateOnCompleting() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-parent.bpmn",
            parentProcess(c -> c.zeebeOutputExpression("assert(x, x != null)", "y")))
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    completeJobWith(Map.of());

    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .describedAs("Expected incident to be created")
        .isTrue();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldNotPropagateVariablesOnTermination() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-parent.bpmn",
            parentProcess(callActivity -> callActivity.zeebeInputExpression("x", "y")))
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).withVariable("x", 1).create();

    final var childInstanceKey = getChildInstanceOf(processInstanceKey).getProcessInstanceKey();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
                .variableRecords())
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getScopeKey(), v.getName()))
        .contains(tuple(childInstanceKey, "y"))
        .doesNotContain(tuple(processInstanceKey, "y"));
  }

  @Test
  public void shouldRejectCancelChildInstanceCommand() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-root.bpmn",
            Bpmn.createExecutableProcess("root")
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(PROCESS_ID_PARENT))
                .done())
        .deploy();

    final var rootInstanceKey = ENGINE.processInstance().ofBpmnProcessId("root").create();

    final var parentInstance = getChildInstanceOf(rootInstanceKey);
    final var childInstance = getChildInstanceOf(parentInstance.getProcessInstanceKey());

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(childInstance.getProcessInstanceKey())
            .expectRejection()
            .cancel();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            String.format(
                "Expected to cancel a process instance with key '%d', "
                    + "but it is created by a parent process instance. "
                    + "Cancel the root process instance '%d' instead.",
                childInstance.getProcessInstanceKey(), rootInstanceKey));
  }

  @Test
  public void shouldNotActivateCallActivityIfIncidentIsCreated() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-parent.bpmn",
            parentProcess(c -> c.zeebeInputExpression("assert(x, x != null)", "y")))
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // then
    final var incidentKey =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 1)).update();

    final var incidentResolved =
        ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentKey).resolve();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.CALL_ACTIVITY)
                .getFirst()
                .getPosition())
        .describedAs("Expected call activity to be ACTIVATED after incident is resolved")
        .isGreaterThan(incidentResolved.getPosition());
  }

  @Test
  public void shouldCreateInstanceOfCalledElementAtNoneStartEvent() {
    // given
    final var processBuilder = Bpmn.createExecutableProcess(PROCESS_ID_CHILD);
    processBuilder.startEvent("none-start").endEvent();
    processBuilder.startEvent("timer-start").timerWithCycle("R/PT1H").endEvent();
    processBuilder.startEvent("message-start").message("start").endEvent();

    ENGINE.deployment().withXmlResource("wf-child.bpmn", processBuilder.done()).deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withParentProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.START_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsOnly("none-start");
  }

  @Test
  public void shouldTriggerBoundaryEventOnChildInstanceTermination() {
    // given two processes with call activities that have an interrupting message boundary event
    final var processLevel1 =
        Bpmn.createExecutableProcess("level1")
            .startEvent()
            .callActivity("call-level2", c -> c.zeebeProcessId("level2"))
            .boundaryEvent()
            .message(m -> m.name("cancel").zeebeCorrelationKeyExpression("key"))
            .endEvent()
            .done();

    final var processLevel2 =
        Bpmn.createExecutableProcess("level2")
            .startEvent()
            .callActivity("call-level3", c -> c.zeebeProcessId("level3"))
            .boundaryEvent()
            .message(m -> m.name("cancel").zeebeCorrelationKeyExpression("key"))
            .endEvent()
            .done();

    final var processLevel3 =
        Bpmn.createExecutableProcess("level3")
            .startEvent()
            .serviceTask("task-level3", t -> t.zeebeJobType("task-level3"))
            .endEvent()
            .done();

    ENGINE
        .deployment()
        .withXmlResource("level1.bpmn", processLevel1)
        .withXmlResource("level2.bpmn", processLevel2)
        .withXmlResource("level3.bpmn", processLevel3)
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("level1").withVariable("key", "key-1").create();

    RecordingExporter.jobRecords(JobIntent.CREATED).withType("task-level3").await();

    // when publish a message to trigger the boundary events and terminate the call activities
    ENGINE.message().withName("cancel").withCorrelationKey("key-1").publish();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldLimitDescendantDepth() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("Loop")
                .startEvent()
                .exclusiveGateway("failsafe")
                .defaultFlow()
                .callActivity(
                    "go_deeper",
                    b ->
                        b.zeebeProcessId("Loop")
                            .zeebeInputExpression("depth + 1", "depth")
                            .zeebePropagateAllParentVariables(false))
                .endEvent("done")
                .moveToLastExclusiveGateway()
                .conditionExpression("depth > 1010")
                .userTask("inspect_failure")
                .endEvent("failed")
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("Loop").withVariable("depth", 1).create();

    // then
    RecordingExporter.setMaximumWaitTime(10_000);
    Assertions.assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst().getValue())
        .describedAs("Expect that incident is raised due to the depth limit")
        .hasErrorMessage(
            """
        The call activity has reached the maximum depth of %d. This is likely due to a recursive call. \
        Cancel the root process instance if this was unintentional. Otherwise, consider increasing the \
        maximum depth, or use process instance modification to adjust the process instance.\
        """
                .formatted(CUSTOM_CALL_ACTIVITY_DEPTH));
  }

  private void completeJobWith(final Map<String, Object> variables) {

    RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).getFirst().getValue();

    ENGINE
        .jobs()
        .withType(jobType)
        .activate()
        .getValue()
        .getJobKeys()
        .forEach(jobKey -> ENGINE.job().withKey(jobKey).withVariables(variables).complete());
  }

  private ProcessInstanceRecordValue getChildInstanceOf(final long processInstanceKey) {

    return RecordingExporter.processInstanceRecords()
        .withParentProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getValue();
  }

  private long getCallActivityInstanceKey(final long processInstanceKey) {

    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.CALL_ACTIVITY)
        .getFirst()
        .getKey();
  }
}
