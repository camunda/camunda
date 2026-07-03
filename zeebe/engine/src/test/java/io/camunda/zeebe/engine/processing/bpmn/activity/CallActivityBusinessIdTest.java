/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.processinstance.BusinessIdValidator;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.CallActivityBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CallActivityBusinessIdTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PARENT_PROCESS_ID = "parent-process";
  private static final String CHILD_PROCESS_ID = "child-process";
  private static final String GRANDCHILD_PROCESS_ID = "grandchild-process";
  private static final String PARENT_BUSINESS_ID = "parent-business-id";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance parentProcess(final Consumer<CallActivityBuilder> consumer) {
    final CallActivityBuilder callActivity =
        Bpmn.createExecutableProcess(PARENT_PROCESS_ID).startEvent().callActivity("call");
    callActivity.zeebeProcessId(CHILD_PROCESS_ID);
    consumer.accept(callActivity);
    return callActivity.endEvent().done();
  }

  private static BpmnModelInstance childProcess() {
    return Bpmn.createExecutableProcess(CHILD_PROCESS_ID).startEvent().endEvent().done();
  }

  private static BpmnModelInstance childProcessCalling(
      final Consumer<CallActivityBuilder> grandchildCallActivity) {
    final CallActivityBuilder callActivity =
        Bpmn.createExecutableProcess(CHILD_PROCESS_ID).startEvent().callActivity("call-grandchild");
    callActivity.zeebeProcessId(GRANDCHILD_PROCESS_ID);
    grandchildCallActivity.accept(callActivity);
    return callActivity.endEvent().done();
  }

  private static BpmnModelInstance grandchildProcess() {
    return Bpmn.createExecutableProcess(GRANDCHILD_PROCESS_ID).startEvent().endEvent().done();
  }

  private void deploy(final BpmnModelInstance parent) {
    ENGINE
        .deployment()
        .withXmlResource("parent.bpmn", parent)
        .withXmlResource("child.bpmn", childProcess())
        .deploy();
  }

  private static Record<ProcessInstanceRecordValue> childProcessInstance(final long parentKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withParentProcessInstanceKey(parentKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst();
  }

  @Test
  public void shouldInheritParentBusinessIdWhenAttributeIsAbsent() {
    // given - a call activity without a businessId attribute
    deploy(parentProcess(c -> {}));

    // when
    final long parentKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PARENT_PROCESS_ID)
            .withBusinessId(PARENT_BUSINESS_ID)
            .create();

    // then - the child inherits the parent's Business ID (8.9 behaviour)
    Assertions.assertThat(childProcessInstance(parentKey).getValue())
        .hasBusinessId(PARENT_BUSINESS_ID);
  }

  @Test
  public void shouldOverrideParentBusinessIdWithLiteral() {
    // given - a literal businessId overriding inheritance
    deploy(parentProcess(c -> c.zeebeBusinessId("child-123")));

    // when
    final long parentKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PARENT_PROCESS_ID)
            .withBusinessId(PARENT_BUSINESS_ID)
            .create();

    // then - the literal wins over the parent's Business ID
    Assertions.assertThat(childProcessInstance(parentKey).getValue()).hasBusinessId("child-123");
  }

  @Test
  public void shouldResolveEmptyBusinessIdToEmpty() {
    // given - an explicitly empty businessId
    deploy(parentProcess(c -> c.zeebeBusinessId("")));

    // when
    final long parentKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PARENT_PROCESS_ID)
            .withBusinessId(PARENT_BUSINESS_ID)
            .create();

    // then - the child starts with no Business ID, not the inherited one
    Assertions.assertThat(childProcessInstance(parentKey).getValue()).hasBusinessId("");
  }

  @Test
  public void shouldEvaluateFeelBusinessIdAtChildCreation() {
    // given - a FEEL businessId referencing a process variable
    deploy(parentProcess(c -> c.zeebeBusinessId("=orderId")));

    // when
    final long parentKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PARENT_PROCESS_ID)
            .withVariable("orderId", "child-xyz")
            .create();

    // then - the expression is evaluated at the call-activity scope and assigned to the child
    Assertions.assertThat(childProcessInstance(parentKey).getValue()).hasBusinessId("child-xyz");
  }

  @Test
  public void shouldAcceptFeelBusinessIdAtMaxLength() {
    // given - a FEEL businessId that resolves to exactly the maximum allowed length
    deploy(parentProcess(c -> c.zeebeBusinessId("=businessIdVar")));
    final String maxLengthBusinessId = "a".repeat(BusinessIdValidator.MAX_BUSINESS_ID_LENGTH);

    // when
    final long parentKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PARENT_PROCESS_ID)
            .withVariable("businessIdVar", maxLengthBusinessId)
            .create();

    // then - the value at the boundary is accepted (no incident) and assigned to the child
    Assertions.assertThat(childProcessInstance(parentKey).getValue())
        .hasBusinessId(maxLengthBusinessId);
  }

  @Test
  public void shouldEvaluateFeelBusinessIdReferencingInputMappedVariable() {
    // given - a call activity whose input mapping produces a local variable that the businessId
    // expression references; input mappings are applied before the businessId is resolved
    deploy(
        parentProcess(
            c ->
                c.zeebeBusinessId("=mappedId")
                    .zeebeInputExpression("\"from-input-mapping\"", "mappedId")));

    // when
    final long parentKey = ENGINE.processInstance().ofBpmnProcessId(PARENT_PROCESS_ID).create();

    // then - the businessId resolves from the input-mapped variable
    Assertions.assertThat(childProcessInstance(parentKey).getValue())
        .hasBusinessId("from-input-mapping");
  }

  @Test
  public void shouldKeepChildBusinessIdImmutableAcrossLifecycle() {
    // given
    deploy(parentProcess(c -> c.zeebeBusinessId("child-123")));

    // when
    final long parentKey = ENGINE.processInstance().ofBpmnProcessId(PARENT_PROCESS_ID).create();
    final long childKey = childProcessInstance(parentKey).getKey();

    // then - the Business ID is stable across the whole child process-instance lifecycle
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(childKey)
                .withElementType(BpmnElementType.PROCESS)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBusinessId())
        .containsOnly("child-123");
  }

  @Test
  public void shouldResolveBusinessIdIndependentlyForNestedCallActivities() {
    // given - parent -> child -> grandchild, each call activity sets its own literal
    ENGINE
        .deployment()
        .withXmlResource("parent.bpmn", parentProcess(c -> c.zeebeBusinessId("level-1")))
        .withXmlResource("child.bpmn", childProcessCalling(c -> c.zeebeBusinessId("level-2")))
        .withXmlResource("grandchild.bpmn", grandchildProcess())
        .deploy();

    // when
    final long parentKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PARENT_PROCESS_ID)
            .withBusinessId(PARENT_BUSINESS_ID)
            .create();

    // then - each level carries the Business ID configured on its own call activity
    final Record<ProcessInstanceRecordValue> childInstance = childProcessInstance(parentKey);
    Assertions.assertThat(childInstance.getValue()).hasBusinessId("level-1");

    final Record<ProcessInstanceRecordValue> grandchildInstance =
        childProcessInstance(childInstance.getKey());
    Assertions.assertThat(grandchildInstance.getValue()).hasBusinessId("level-2");
  }

  @Test
  public void shouldInheritGrandchildBusinessIdFromChildNotParent() {
    // given - the child overrides its Business ID, the grandchild's call activity sets none
    ENGINE
        .deployment()
        .withXmlResource("parent.bpmn", parentProcess(c -> c.zeebeBusinessId("child-level")))
        .withXmlResource("child.bpmn", childProcessCalling(c -> {}))
        .withXmlResource("grandchild.bpmn", grandchildProcess())
        .deploy();

    // when
    final long parentKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PARENT_PROCESS_ID)
            .withBusinessId(PARENT_BUSINESS_ID)
            .create();

    // then - the grandchild inherits the child's Business ID, not the (different) parent's
    final Record<ProcessInstanceRecordValue> childInstance = childProcessInstance(parentKey);
    Assertions.assertThat(childInstance.getValue()).hasBusinessId("child-level");

    final Record<ProcessInstanceRecordValue> grandchildInstance =
        childProcessInstance(childInstance.getKey());
    Assertions.assertThat(grandchildInstance.getValue()).hasBusinessId("child-level");
  }

  @Test
  public void shouldInheritParentBusinessIdThroughNestedCallActivities() {
    // given - neither the child nor the grandchild call activity sets a Business ID
    ENGINE
        .deployment()
        .withXmlResource("parent.bpmn", parentProcess(c -> {}))
        .withXmlResource("child.bpmn", childProcessCalling(c -> {}))
        .withXmlResource("grandchild.bpmn", grandchildProcess())
        .deploy();

    // when
    final long parentKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PARENT_PROCESS_ID)
            .withBusinessId(PARENT_BUSINESS_ID)
            .create();

    // then - the parent's Business ID propagates all the way down
    final Record<ProcessInstanceRecordValue> childInstance = childProcessInstance(parentKey);
    Assertions.assertThat(childInstance.getValue()).hasBusinessId(PARENT_BUSINESS_ID);

    final Record<ProcessInstanceRecordValue> grandchildInstance =
        childProcessInstance(childInstance.getKey());
    Assertions.assertThat(grandchildInstance.getValue()).hasBusinessId(PARENT_BUSINESS_ID);
  }

  @Test
  public void shouldDeriveBusinessIdPerInstanceForMultiInstanceCallActivity() {
    // given - a multi-instance call activity deriving the Business ID from each element
    final CallActivityBuilder callActivity =
        Bpmn.createExecutableProcess(PARENT_PROCESS_ID).startEvent().callActivity("call");
    callActivity.zeebeProcessId(CHILD_PROCESS_ID).zeebeBusinessId("=\"businessId-\" + item");
    callActivity.multiInstance(
        b -> b.zeebeInputCollectionExpression("items").zeebeInputElement("item"));
    deploy(callActivity.endEvent().done());

    // when
    final long parentKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PARENT_PROCESS_ID)
            .withVariable("items", List.of("1", "2", "3"))
            .create();

    // then - each child instance derives its own Business ID at its own scope
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withParentProcessInstanceKey(parentKey)
                .withBpmnProcessId(CHILD_PROCESS_ID)
                .filterRootScope()
                .limit(3))
        .extracting(r -> r.getValue().getBusinessId())
        .containsExactlyInAnyOrder("businessId-1", "businessId-2", "businessId-3");
  }
}
