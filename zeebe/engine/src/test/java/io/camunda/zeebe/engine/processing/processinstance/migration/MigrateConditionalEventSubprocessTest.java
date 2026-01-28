/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.engine.processing.processinstance.migration.MigrationTestUtil.extractProcessDefinitionKeyByProcessId;
import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateConditionalEventSubprocessTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteMigratedEventForActiveConditionalEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                                .serviceTask("A", t -> t.zeebeJobType("task1"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask1")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .eventSubProcess(
                        "sub2",
                        s ->
                            s.startEvent("start2")
                                .condition(c -> c.condition("=x > 999").zeebeVariableNames("x"))
                                .serviceTask("B", t -> t.zeebeJobType("task2"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // trigger event subprocess in the source
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 11)).update();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("sub1", "sub2")
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key is changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("sub2")
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    // conditional subscriptions do NOT contain bpmnProcessId, so we only assert on fields available
    Assertions.assertThat(
            RecordingExporter.records()
                .limit(
                    r ->
                        r.getKey() == processInstanceKey
                            && r.getIntent() == ProcessInstanceMigrationIntent.MIGRATED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withCatchEventId("start2")
                .skip(1)
                .exists())
        .describedAs(
            "Expect that no conditional subscription is created after migration because "
                + "the event subprocess is already active (already triggered)")
        .isFalse();
  }

  @Test
  public void shouldWriteMigratedEventForMultipleActiveConditionalEventSubprocesses() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                                .interrupting(false)
                                .serviceTask("A", t -> t.zeebeJobType("task1"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask1")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .eventSubProcess(
                        "sub2",
                        s ->
                            s.startEvent("start2")
                                .condition(c -> c.condition("=x > 999").zeebeVariableNames("x"))
                                .interrupting(false)
                                .serviceTask("B", t -> t.zeebeJobType("task2"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // trigger two non-interrupting instances
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 11)).update();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 12)).update();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .skip(1)
        .await();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .addMappingInstruction("sub1", "sub2")
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    org.assertj.core.api.Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
                .limit(2))
        .hasSize(2)
        .extracting(io.camunda.zeebe.protocol.record.Record::getValue)
        .describedAs("Expect that process definition key is changed")
        .allMatch(v -> v.getProcessDefinitionKey() == targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .allMatch(v -> v.getBpmnProcessId().equals(targetProcessId))
        .allMatch(v -> v.getElementId().equals("sub2"))
        .describedAs("Expect that version number did not change")
        .allMatch(v -> v.getVersion() == 1);
  }

  @Test
  public void shouldMigrateConditionalForMappedStartEvent() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .condition(
                                    c ->
                                        c.condition("=x > 10")
                                            .zeebeVariableNames("x")
                                            .zeebeVariableEvents("create"))
                                .userTask("subUserTask1")
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask1")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .eventSubProcess(
                        "sub2",
                        s ->
                            s.startEvent("start2")
                                .condition(
                                    c ->
                                        c.condition("=y > 10")
                                            .zeebeVariableNames("y")
                                            .zeebeVariableEvents("update"))
                                .userTask("subUserTask2")
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withCatchEventId("start1")
        .await();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .addMappingInstruction("start1", "start2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("start2")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the bpmn process id is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the catch event id is updated")
        .hasCatchEventId("start2")
        .describedAs("Expect that the migrated subscription keeps the source condition")
        .hasCondition("=x > 10")
        .describedAs("Expect that the migrated subscription keeps the source variable names")
        .hasVariableNames("x")
        .describedAs("Expect that the migrated subscription keeps the source variable events")
        .hasVariableEvents("create");

    // trigger the migrated subscription
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 11)).update();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .withElementId("subUserTask2")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process instance is continued in the target process")
        .isNotNull();
  }

  @Test
  public void shouldUnsubscribeFromConditionalEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub",
                        s ->
                            s.startEvent("start1")
                                .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                                .serviceTask("A", t -> t.zeebeJobType("task"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask1")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withCatchEventId("start1")
        .await();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId(targetProcessId)
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    // conditional subscription DELETED
    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("start1")
                .exists())
        .describedAs("Expect that the conditional subscription for the event subprocess is deleted")
        .isTrue();
  }

  @Test
  public void shouldSubscribeToConditionalEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .userTask("userTask1")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                                .userTask("eventSubprocessUserTask")
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("userTask1")
        .await();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId(targetProcessId)
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    // created subscription in target
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withCatchEventId("start1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the conditional subscription is created for the target process")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasCatchEventId("start1")
        .hasCondition("=x > 10");

    // trigger it
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 11)).update();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .withElementId("eventSubprocessUserTask")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process instance is continued in the target process")
        .isNotNull();
  }

  @Test
  public void shouldResubscribeToConditionalEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                                .serviceTask("A", t -> t.zeebeJobType("task1"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask1")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .eventSubProcess(
                        "sub2",
                        s ->
                            s.startEvent("start2")
                                .condition(c -> c.condition("=y > 10").zeebeVariableNames("y"))
                                .userTask("eventSubprocessUserTask")
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withCatchEventId("start1")
        .await();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when (no mapping for start event -> resubscribe semantics)
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .migrate();

    // then: old deleted
    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("start1")
                .exists())
        .describedAs("Expect that the conditional subscription in the source is deleted")
        .isTrue();

    // then: new created
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withCatchEventId("start2")
                .getFirst()
                .getValue())
        .describedAs("Expect that the conditional subscription is created in the target process")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasCatchEventId("start2")
        .hasCondition("=y > 10");

    // validate trigger
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("y", 11)).update();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .withElementId("eventSubprocessUserTask")
                .getFirst()
                .getValue())
        .isNotNull();
  }

  @Test
  public void shouldKeepScopeKeyWhenMigratingConditionalEventSubprocessStartSubscription() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask1")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .eventSubProcess(
                        "sub2",
                        s ->
                            s.startEvent("start2")
                                .condition(c -> c.condition("=x > 999").zeebeVariableNames("x"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final var created =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withCatchEventId("start1")
            .getFirst();
    final long originalScopeKey = created.getValue().getScopeKey();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .addMappingInstruction("start1", "start2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("start2")
                .getFirst()
                .getValue()
                .getScopeKey())
        .describedAs("Expect that scope key remains stable across migration for start subscription")
        .isEqualTo(originalScopeKey);
  }
}
