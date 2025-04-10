/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.engine.processing.processinstance.migration.MigrationTestUtil.extractProcessDefinitionKeyByProcessId;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateProcessInstanceWithMessageStartEventTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  /**
   * Verify published message can be correlated to the start event of the source process definition
   * after the migration. Since target process definition has a different message name (msg2), a new
   * process instance with message start event (msg1) should be allowed.
   */
  @Test
  public void shouldAdjustMessageCardinalityTrackingWhenMigratedForProcessInstance() {
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("msg_start")
                    .message("msg1")
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("msg_start")
                    .message("msg2")
                    .serviceTask("B", t -> t.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();

    ENGINE.message().withName("msg1").withCorrelationKey("cardinality").publish();
    final var processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.START_EVENT)
            .withElementId("msg_start")
            .withBpmnProcessId(processId)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    // when
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    ENGINE.message().withName("msg1").withCorrelationKey("cardinality").publish();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.PROCESS)
                .withBpmnProcessId(processId)
                .withElementId(processId)
                .skip(1) // skip the first activation
                .exists())
        .describedAs("Expect that a new process is created with message name 'msg1'.")
        .isTrue();
  }

  /**
   * Published message cannot be correlated to the start event of the target process definition
   * after the migration until the target process is completed. Once the target process is
   * completed, the message with the same correlation key should be correlated to the target
   * process.
   */
  @Test
  public void shouldAdjustMessageCardinalityTrackingWhenMigratedForTargetProcess() {
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("msg_start")
                    .message("msg1")
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("msg_start")
                    .message("msg2")
                    .serviceTask("B", t -> t.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();

    ENGINE.message().withName("msg1").withCorrelationKey("cardinality").publish();
    final var processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.START_EVENT)
            .withElementId("msg_start")
            .withBpmnProcessId(processId)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    // when
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    final long messagePosition =
        ENGINE
            .message()
            .withName("msg2")
            .withCorrelationKey("cardinality")
            .publish()
            .getSourceRecordPosition();

    // broadcast a record to have something to limit the stream on
    final var limitPosition = ENGINE.signal().withSignalName("dummy").broadcast().getPosition();
    Assertions.assertThat(
            RecordingExporter.records()
                .between(messagePosition, limitPosition)
                .processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(r -> r.getValue().getElementId())
        .describedAs("Expect that the target process is not activated")
        .doesNotContain(targetProcessId);

    // complete existing target instance
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    // start new target instance
    ENGINE.message().withName("msg2").withCorrelationKey("cardinality").publish();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.PROCESS)
                .withBpmnProcessId(targetProcessId)
                .exists())
        .describedAs(
            "Expect that the target process is only activated with 'msg2' after the target process is completed.")
        .isTrue();
  }

  /**
   * After migrating a process instance started by a message, we expect that a buffered message with
   * the same correlation key cannot be correlated to the migrated instance until the process
   * instance completes. We expect that the buffered message is automatically correlated once the
   * migrated instance finishes.
   */
  @Test
  public void shouldAdjustMessageCardinalityTrackingWhenMigratedForTargetProcessWithMessageTTL() {
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("msg_start")
                    .message("msg1")
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("msg_start")
                    .message("msg2")
                    .serviceTask("B", t -> t.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();

    ENGINE.message().withName("msg1").withCorrelationKey("cardinality").publish();
    final var processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.START_EVENT)
            .withElementId("msg_start")
            .withBpmnProcessId(processId)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    // when
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    final long messagePosition =
        ENGINE
            .message()
            .withName("msg2")
            .withCorrelationKey("cardinality")
            .withTimeToLive(Duration.ofMinutes(1)) // create message with TTL
            .publish()
            .getSourceRecordPosition();

    // broadcast a record to have something to limit the stream on
    final var limitPosition = ENGINE.signal().withSignalName("dummy").broadcast().getPosition();
    Assertions.assertThat(
            RecordingExporter.records()
                .between(messagePosition, limitPosition)
                .processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(r -> r.getValue().getElementId())
        .describedAs("Expect that the target process is not activated")
        .doesNotContain(targetProcessId);

    // complete existing target instance
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.PROCESS)
                .withBpmnProcessId(targetProcessId)
                .withElementId(targetProcessId)
                .exists())
        .describedAs(
            "Expect that the target process is only activated with 'msg2' after the target process is completed. But this time a buffered message is correlated.")
        .isTrue();
  }
}
