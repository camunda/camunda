/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.CallActivityBuilder;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.FeatureFlags;
import java.util.List;
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
          .withEngineConfig(config -> config.setMaxProcessDepth(CUSTOM_CALL_ACTIVITY_DEPTH))
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

  private static BpmnModelInstance childProcess(
      final String jobType, final Consumer<ServiceTaskBuilder> consumer) {
    final var builder =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD)
            .startEvent()
            .serviceTask("child-task", t -> t.zeebeJobType(jobType));

    consumer.accept(builder);

    return builder.endEvent().done();
  }

  @Before
  public void init() {
    jobType = helper.getJobType();
  }

  @Test
  public void shouldActivateCallActivity() {
    // given
    deployDefaultParentAndChildProcess();

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
    // given
    deployDefaultParentAndChildProcess();

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
  public void shouldCreateInstanceOfLatestVersionOfCalledElementIfBindingTypeNotSet() {
    // given
    final var parentProcess = parentProcess(CallActivityBuilder::done);
    final var childProcessV1 =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD).startEvent("v1").endEvent().done();
    final var childProcessV2 =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD).startEvent("v2").endEvent().done();
    ENGINE
        .deployment()
        .withXmlResource("wf-parent.bpmn", parentProcess)
        .withXmlResource("wf-child.bpmn", childProcessV1)
        .deploy();
    final var deployment =
        ENGINE.deployment().withXmlResource("wf-child.bpmn", childProcessV2).deploy();
    final var latestDeployedVersion = deployment.getValue().getProcessesMetadata().getFirst();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // then
    Assertions.assertThat(getChildInstanceOf(processInstanceKey))
        .hasVersion(latestDeployedVersion.getVersion())
        .hasProcessDefinitionKey(latestDeployedVersion.getProcessDefinitionKey());
  }

  @Test
  public void shouldCreateInstanceOfLatestVersionOfCalledElementForBindingTypeLatest() {
    // given
    final var parentProcess =
        parentProcess(builder -> builder.zeebeBindingType(ZeebeBindingType.latest));
    final var childProcessV1 =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD).startEvent("v1").endEvent().done();
    final var childProcessV2 =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD).startEvent("v2").endEvent().done();
    ENGINE
        .deployment()
        .withXmlResource("wf-parent.bpmn", parentProcess)
        .withXmlResource("wf-child.bpmn", childProcessV1)
        .deploy();
    final var deployment =
        ENGINE.deployment().withXmlResource("wf-child.bpmn", childProcessV2).deploy();
    final var latestDeployedVersion = deployment.getValue().getProcessesMetadata().getFirst();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // then
    Assertions.assertThat(getChildInstanceOf(processInstanceKey))
        .hasVersion(latestDeployedVersion.getVersion())
        .hasProcessDefinitionKey(latestDeployedVersion.getProcessDefinitionKey());
  }

  @Test
  public void shouldCreateInstanceOfVersionInSameDeploymentForBindingTypeDeployment() {
    // given
    final var parentProcess =
        parentProcess(builder -> builder.zeebeBindingType(ZeebeBindingType.deployment));
    final var childProcessV1 =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD).startEvent("v1").endEvent().done();
    final var childProcessV2 =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD).startEvent("v2").endEvent().done();
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource("wf-parent.bpmn", parentProcess)
            .withXmlResource("wf-child.bpmn", childProcessV1)
            .deploy();
    final var versionInSameDeployment =
        deployment.getValue().getProcessesMetadata().stream()
            .filter(metadata -> PROCESS_ID_CHILD.equals(metadata.getBpmnProcessId()))
            .findFirst()
            .orElseThrow();
    ENGINE.deployment().withXmlResource("wf-child.bpmn", childProcessV2).deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // then
    Assertions.assertThat(getChildInstanceOf(processInstanceKey))
        .hasVersion(versionInSameDeployment.getVersion())
        .hasProcessDefinitionKey(versionInSameDeployment.getProcessDefinitionKey());
  }

  @Test
  public void shouldCreateInstanceOfLatestVersionWithGivenVersionTagForBindingTypeVersionTag() {
    // given
    final var parentProcess =
        parentProcess(
            builder ->
                builder.zeebeBindingType(ZeebeBindingType.versionTag).zeebeVersionTag("v1.0"));
    final var childProcessV1Old =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD)
            .versionTag("v1.0")
            .startEvent("old")
            .endEvent()
            .done();
    final var childProcessV1New =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD)
            .versionTag("v1.0")
            .startEvent("new")
            .endEvent()
            .done();
    final var childProcessV2 =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD)
            .versionTag("v2.0")
            .startEvent()
            .endEvent()
            .done();
    final var childProcessWithoutVersionTag =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD).startEvent().endEvent().done();
    ENGINE
        .deployment()
        .withXmlResource("wf-parent.bpmn", parentProcess)
        .withXmlResource("wf-child.bpmn", childProcessV1Old)
        .deploy();
    final var deployment =
        ENGINE.deployment().withXmlResource("wf-child.bpmn", childProcessV1New).deploy();
    final var deployedChildProcessV1New = deployment.getValue().getProcessesMetadata().getFirst();
    ENGINE.deployment().withXmlResource("wf-child.bpmn", childProcessV2).deploy();
    ENGINE.deployment().withXmlResource("wf-child.bpmn", childProcessWithoutVersionTag).deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // then
    Assertions.assertThat(getChildInstanceOf(processInstanceKey))
        .hasVersion(deployedChildProcessV1New.getVersion())
        .hasProcessDefinitionKey(deployedChildProcessV1New.getProcessDefinitionKey());
  }

  @Test
  public void shouldHaveReferenceToParentInstance() {
    // given
    deployDefaultParentAndChildProcess();

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
    // given
    deployDefaultParentAndChildProcess();

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
    // given
    deployDefaultParentAndChildProcess();

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
    deployDefaultParentAndChildProcess();
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
        .withXmlResource("wf-child.bpmn", childProcess(jobType, ServiceTaskBuilder::done))
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
        .withXmlResource("wf-child.bpmn", childProcess(jobType, ServiceTaskBuilder::done))
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
        .withXmlResource("wf-child.bpmn", childProcess(jobType, ServiceTaskBuilder::done))
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
  public void shouldCreateInstanceOfCalledElementWithExpressionAndBindingTypeDeployment() {
    // given
    final var childProcessV1 =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD).startEvent("v1").endEvent().done();
    final var childProcessV2 =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD).startEvent("v2").endEvent().done();
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                "wf-parent.bpmn",
                parentProcess(
                    callActivity ->
                        callActivity
                            .zeebeProcessIdExpression("processId")
                            .zeebeBindingType(ZeebeBindingType.deployment)))
            .withXmlResource("wf-child.bpmn", childProcessV1)
            .deploy();
    final var versionInSameDeployment =
        deployment.getValue().getProcessesMetadata().stream()
            .filter(metadata -> PROCESS_ID_CHILD.equals(metadata.getBpmnProcessId()))
            .findFirst()
            .orElseThrow();
    ENGINE.deployment().withXmlResource("wf-child.bpmn", childProcessV2).deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID_PARENT)
            .withVariable("processId", PROCESS_ID_CHILD)
            .create();

    // then
    Assertions.assertThat(getChildInstanceOf(processInstanceKey))
        .hasBpmnProcessId(PROCESS_ID_CHILD)
        .hasProcessDefinitionKey(versionInSameDeployment.getProcessDefinitionKey());
  }

  @Test
  public void shouldCreateInstanceOfCalledElementWithExpressionAndBindingTypeVersionTag() {
    final var childProcess =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD)
            .versionTag("v1.0")
            .startEvent()
            .endEvent()
            .done();
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-parent.bpmn",
            parentProcess(
                callActivity ->
                    callActivity
                        .zeebeProcessIdExpression("processId")
                        .zeebeBindingType(ZeebeBindingType.versionTag)
                        .zeebeVersionTag("v1.0")))
        .deploy();
    ENGINE.deployment().withXmlResource("wf-child.bpmn", childProcess).deploy();

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
        .withXmlResource("wf-child.bpmn", childProcess(jobType, ServiceTaskBuilder::done))
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
    deployDefaultParentAndChildProcess();
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
        .withXmlResource("wf-child.bpmn", childProcess(jobType, ServiceTaskBuilder::done))
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

    assertThat(getCallActivityInstance(processInstanceKey).getPosition())
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
  public void shouldPropagateTreePathPropertiesWithIncident() {
    // given
    final String rootProcessId = "root";
    final String callActivity1Id = "callParent";
    final String callActivity2Id = "callChild";
    // every deployment resource will have indexes starting from zero
    final var ca1Index = 0;
    final var ca2Index = 0;
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-root.bpmn",
            Bpmn.createExecutableProcess(rootProcessId)
                .startEvent()
                .callActivity(callActivity1Id, c -> c.zeebeProcessId(PROCESS_ID_PARENT))
                .done())
        .withXmlResource(
            "wf-parent.bpmn",
            parentProcess(c -> c.id(callActivity2Id).zeebeProcessId(PROCESS_ID_CHILD)))
        .withXmlResource(
            "wf-child.bpmn",
            childProcess(jobType, c -> c.zeebeOutputExpression("assert(x, x != null)", "y")))
        .deploy();

    final var rootInstanceKey = ENGINE.processInstance().ofBpmnProcessId(rootProcessId).create();

    final var rootInstance = getProcessInstanceRecordValue(rootInstanceKey);
    final var parentInstance = getChildInstanceOf(rootInstanceKey);
    final var parentInstanceKey = parentInstance.getProcessInstanceKey();
    final var childInstance = getChildInstanceOf(parentInstanceKey);
    final long childInstanceKey = childInstance.getProcessInstanceKey();
    final var callActivity1Key = getCallActivityInstanceKey(rootInstanceKey);
    final var callActivity2Key = getCallActivityInstanceKey(parentInstanceKey);

    completeJobWith(Map.of());

    // then
    final IncidentRecordValue incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(childInstanceKey)
            .getFirst()
            .getValue();

    Assertions.assertThat(incident)
        .hasOnlyElementInstancePath(
            List.of(rootInstanceKey, callActivity1Key),
            List.of(parentInstanceKey, callActivity2Key),
            List.of(childInstanceKey, incident.getElementInstanceKey()))
        .hasOnlyProcessDefinitionPath(
            rootInstance.getProcessDefinitionKey(),
            parentInstance.getProcessDefinitionKey(),
            childInstance.getProcessDefinitionKey())
        .hasOnlyCallingElementPath(ca1Index, ca2Index);
  }

  @Test
  public void shouldPropagateCorrectIndexesInCallingElementPathWhenMultipleProcessesInSameFile() {
    // given
    final String rootProcessId = "root-process";
    // call activities { "call-parent", "call-child" } when sorted wll be {"call-child",
    // "call-parent"}
    final var ca1Index = 1;
    final var ca2Index = 0;

    ENGINE.deployment().withXmlClasspathResource("/processes/callActivity.bpmn").deploy();
    final var rootInstanceKey = ENGINE.processInstance().ofBpmnProcessId(rootProcessId).create();

    final var rootInstance = getProcessInstanceRecordValue(rootInstanceKey);
    final var parentInstance = getChildInstanceOf(rootInstanceKey);
    final var parentInstanceKey = parentInstance.getProcessInstanceKey();
    final var childInstance = getChildInstanceOf(parentInstanceKey);
    final long childInstanceKey = childInstance.getProcessInstanceKey();
    final var callActivity1Key = getCallActivityInstanceKey(rootInstanceKey);
    final var callActivity2Key = getCallActivityInstanceKey(parentInstanceKey);

    completeJobWith(Map.of());

    // then
    final IncidentRecordValue incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(childInstanceKey)
            .getFirst()
            .getValue();

    Assertions.assertThat(incident)
        .hasOnlyElementInstancePath(
            List.of(rootInstanceKey, callActivity1Key),
            List.of(parentInstanceKey, callActivity2Key),
            List.of(childInstanceKey, incident.getElementInstanceKey()))
        .hasOnlyProcessDefinitionPath(
            rootInstance.getProcessDefinitionKey(),
            parentInstance.getProcessDefinitionKey(),
            childInstance.getProcessDefinitionKey())
        .hasOnlyCallingElementPath(ca1Index, ca2Index);
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
                .conditionExpression("depth > %d".formatted(CUSTOM_CALL_ACTIVITY_DEPTH + 1))
                .userTask("inspect_failure")
                .endEvent("failed")
                .done())
        .deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId("Loop").withVariable("depth", 1).create();

    // then
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withElementId("go_deeper")
            .getFirst()
            .getValue();
    Assertions.assertThat(incident)
        .describedAs("Expect that incident is raised due to the depth limit")
        .hasErrorMessage(
            """
            The call activity has reached the maximum depth of %d. This is likely due to a recursive call. \
            Cancel the root process instance if this was unintentional. Otherwise, consider increasing the \
            maximum depth, or use process instance modification to adjust the process instance."""
                .formatted(CUSTOM_CALL_ACTIVITY_DEPTH));

    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withName("depth")
                .withScopeKey(incident.getElementInstanceKey())
                .getFirst()
                .getValue())
        .describedAs("Expect that we cannot call the call activity with a depth greater than 10")
        .hasValue("%d".formatted(CUSTOM_CALL_ACTIVITY_DEPTH + 1));
  }

  private void deployDefaultParentAndChildProcess() {
    final var parentProcess = parentProcess(CallActivityBuilder::done);

    final var childProcess = childProcess(jobType, ServiceTaskBuilder::done);

    ENGINE
        .deployment()
        .withXmlResource("wf-parent.bpmn", parentProcess)
        .withXmlResource("wf-child.bpmn", childProcess)
        .deploy();
  }

  private static ProcessInstanceRecordValue getProcessInstanceRecordValue(
      final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getValue();
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
    return getCallActivityInstance(processInstanceKey).getKey();
  }

  private static Record<ProcessInstanceRecordValue> getCallActivityInstance(
      final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.CALL_ACTIVITY)
        .getFirst();
  }
}
