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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateMessageEventSubprocessTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteMigratedEventForInterruptingActiveMessageEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent()
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
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
                            s.startEvent()
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
                                .serviceTask("B", t -> t.zeebeJobType("task2"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key", helper.getCorrelationValue())
            .create();

    engine.message().withName("msg").withCorrelationKey(helper.getCorrelationValue()).publish();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    engine
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

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg")
                .withCorrelationKey(helper.getCorrelationValue())
                .skip(1)
                .exists())
        .describedAs(
            "Expect that no message subscription is created after migration because it is an interrupting subprocess")
        .isFalse();
  }

  @Test
  public void shouldWriteMigratedEventForNonInterruptingActiveMessageEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .message(m -> m.name("msg1").zeebeCorrelationKeyExpression("key"))
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
                                .message(m -> m.name("msg2").zeebeCorrelationKeyExpression("key"))
                                .interrupting(false)
                                .serviceTask("B", t -> t.zeebeJobType("task2"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key", helper.getCorrelationValue())
            .create();

    engine.message().withName("msg1").withCorrelationKey(helper.getCorrelationValue()).publish();
    engine.message().withName("msg1").withCorrelationKey(helper.getCorrelationValue()).publish();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("A")
                .limit(2))
        .describedAs("Expect that the non-interrupting event subprocess is activated twice")
        .hasSize(2);

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .addMappingInstruction("sub1", "sub2")
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
                .limit(2))
        .hasSize(2)
        .extracting(Record::getValue)
        .describedAs("Expect that process definition key is changed")
        .allMatch(v -> v.getProcessDefinitionKey() == targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .allMatch(v -> v.getBpmnProcessId().equals(targetProcessId))
        .allMatch(v -> v.getElementId().equals("sub2"))
        .describedAs("Expect that version number did not change")
        .allMatch(v -> v.getVersion() == 1);
  }

  /**
   * This test case is a special case where we have two non-interrupting event subprocesses with
   * start event of the same message name. We have a known limitation where we cannot re-subscribe
   * to the same message name for a message subscription. The workaround for it is to provide a
   * mapping for the start events of the event subprocesses. In the below test case, message names
   * "msg" are the same, and we provide a mapping for the start events as "start1" -> "start2".
   */
  @Test
  public void
      shouldWriteMigratedEventForNonInterruptingActiveMessageEventSubprocessWithSameMessageName() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
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
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
                                .interrupting(false)
                                .serviceTask("B", t -> t.zeebeJobType("task2"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key", helper.getCorrelationValue())
            .create();

    engine.message().withName("msg").withCorrelationKey(helper.getCorrelationValue()).publish();
    engine.message().withName("msg").withCorrelationKey(helper.getCorrelationValue()).publish();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("A")
                .limit(2))
        .describedAs("Expect that the non-interrupting event subprocess is activated twice")
        .hasSize(2);

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .addMappingInstruction("sub1", "sub2")
        .addMappingInstruction("A", "B")
        .addMappingInstruction("start1", "start2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
                .limit(2))
        .hasSize(2)
        .extracting(Record::getValue)
        .describedAs("Expect that process definition key is changed")
        .allMatch(v -> v.getProcessDefinitionKey() == targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .allMatch(v -> v.getBpmnProcessId().equals(targetProcessId))
        .allMatch(v -> v.getElementId().equals("sub2"))
        .describedAs("Expect that version number did not change")
        .allMatch(v -> v.getVersion() == 1);
  }

  @Test
  public void shouldMigrateMessageEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key1"))
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
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key2"))
                                .userTask("eventSubprocessUserTask")
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariable("key1", "key1").create();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .addMappingInstruction("start1", "start2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("start2")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");

    engine.message().withName("msg").withCorrelationKey("key1").publish();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("eventSubprocessUserTask")
                .getFirst())
        .describedAs(
            "Expect that the element inside the event subprocess in the target process is activated")
        .isNotNull();
  }

  @Test
  public void shouldUnsubscribeFromMessageEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub",
                        s ->
                            s.startEvent("start1")
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key1"))
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
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariable("key1", "key1").create();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(processId)
        .hasElementId("start1")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(processId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");
  }

  @Test
  public void shouldSubscribeToMessageEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        engine
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
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key1"))
                                .userTask("eventSubprocessUserTask")
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariable("key1", "key1").create();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("start1")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");

    engine.message().withName("msg").withCorrelationKey("key1").publish();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("eventSubprocessUserTask")
                .getFirst())
        .describedAs(
            "Expect that the element inside the event subprocess in the target process is activated")
        .isNotNull();
  }

  @Test
  public void shouldResubscribeToMessageEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .message(m -> m.name("msg1").zeebeCorrelationKeyExpression("key1"))
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
                                .message(m -> m.name("msg2").zeebeCorrelationKeyExpression("key2"))
                                .userTask("eventSubprocessUserTask")
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("key1", "key1", "key2", "key2"))
            .create();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(processId)
        .hasElementId("start1")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(processId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg2")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("start2")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key2");

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg2")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key2");

    engine.message().withName("msg2").withCorrelationKey("key2").publish();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("eventSubprocessUserTask")
                .getFirst())
        .describedAs(
            "Expect that the element inside the event subprocess in the target process is activated")
        .isNotNull();
  }
}
