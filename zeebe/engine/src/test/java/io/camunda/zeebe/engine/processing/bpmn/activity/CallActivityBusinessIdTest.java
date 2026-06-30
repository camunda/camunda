/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CallActivityBusinessIdTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PARENT_PROCESS_ID = "parent-process";
  private static final String CHILD_PROCESS_ID = "child-process";
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
}
