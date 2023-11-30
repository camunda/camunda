/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class MigrateProcessInstanceTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldMigrateProcessInstanceWithToASmallDifferenceProcessDefinition() {
    // given
    final String processId = helper.getBpmnProcessId();
    final long definitionKey =
        CLIENT_RULE.deployProcess(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .endEvent()
                .done());
    final long processInstanceKey = CLIENT_RULE.createProcessInstance(definitionKey);

    final long serviceTaskKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst()
            .getKey();

    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("A")
            .getFirst()
            .getKey();

    // deploy a new version of the process
    final long targetProcessDefinitionKey =
        CLIENT_RULE.deployProcess(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("B", a -> a.zeebeJobType("B"))
                .endEvent("end")
                .done());

    // when
    final var command =
        CLIENT_RULE
            .getClient()
            .newMigrateProcessInstanceCommand(processInstanceKey)
            .migrationPlan(targetProcessDefinitionKey)
            .addMappingInstruction("A", "B")
            .send();

    // then
    assertThatNoException()
        .describedAs("Expect that migration command is not rejected")
        .isThrownBy(command::join);

    final var migratedProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue();

    final var migratedTaskA =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
            .withRecordKey(serviceTaskKey)
            .getFirst()
            .getValue();

    final var migratedJobA =
        RecordingExporter.jobRecords(JobIntent.MIGRATED)
            .withRecordKey(jobKey)
            .getFirst()
            .getValue();

    assertThat(migratedProcessInstance)
        .describedAs("Expect that process definition key changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id stayed the same")
        .hasBpmnProcessId(processId)
        .hasElementId(processId)
        .describedAs("Expect that version number did not change")
        .hasVersion(2);

    assertThat(migratedTaskA)
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(processId)
        .hasVersion(2)
        .describedAs("Expect that element id changed due to mapping")
        .hasElementId("B");

    assertThat(migratedJobA)
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(processId)
        .hasProcessDefinitionVersion(2)
        .describedAs("Expect that element id changed due to mapping")
        .hasElementId("B")
        .describedAs(
            "Expect that the type did not change even though it's different in the target process."
                + " Re-evaluation of the job type expression is not enabled for this migration")
        .hasType("A");

    CLIENT_RULE.getClient().newCompleteCommand(jobKey).send().join();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withRecordKey(serviceTaskKey)
        .await();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("end")
                .findAny())
        .describedAs("Expect that end event is activated")
        .isPresent();
  }

  @Test
  public void shouldMigrateProcessInstanceWithToALargeDifferenceProcessDefinition() {
    // given
    final String processId = helper.getBpmnProcessId();
    final long definitionKey =
        CLIENT_RULE.deployProcess(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .endEvent()
                .done());
    final long processInstanceKey = CLIENT_RULE.createProcessInstance(definitionKey);

    final long serviceTaskKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst()
            .getKey();

    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("A")
            .getFirst()
            .getKey();

    // deploy a new version of the process
    final long targetProcessDefinitionKey =
        CLIENT_RULE.deployProcess(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("U")
                .serviceTask("B", a -> a.zeebeJobType("B"))
                .subProcess("sub", s -> s.embeddedSubProcess().startEvent().endEvent())
                .endEvent()
                .done());

    // when
    final var command =
        CLIENT_RULE
            .getClient()
            .newMigrateProcessInstanceCommand(processInstanceKey)
            .migrationPlan(targetProcessDefinitionKey)
            .addMappingInstruction("A", "B")
            .send();

    // then
    assertThatNoException()
        .describedAs("Expect that migration command is not rejected")
        .isThrownBy(command::join);

    final var migratedProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue();

    final var migratedTaskA =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
            .withRecordKey(serviceTaskKey)
            .getFirst()
            .getValue();

    final var migratedJobA =
        RecordingExporter.jobRecords(JobIntent.MIGRATED)
            .withRecordKey(jobKey)
            .getFirst()
            .getValue();

    assertThat(migratedProcessInstance)
        .describedAs("Expect that process definition key changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id stayed the same")
        .hasBpmnProcessId(processId)
        .hasElementId(processId)
        .describedAs("Expect that version number did not change")
        .hasVersion(2);

    assertThat(migratedTaskA)
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(processId)
        .hasVersion(2)
        .describedAs("Expect that element id changed due to mapping")
        .hasElementId("B");

    assertThat(migratedJobA)
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(processId)
        .hasProcessDefinitionVersion(2)
        .describedAs("Expect that element id changed due to mapping")
        .hasElementId("B")
        .describedAs(
            "Expect that the type did not change even though it's different in the target process."
                + " Re-evaluation of the job type expression is not enabled for this migration")
        .hasType("A");

    CLIENT_RULE.getClient().newCompleteCommand(jobKey).send().join();
    RecordingExporter.jobRecords(JobIntent.COMPLETED).withRecordKey(jobKey).await();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("sub")
                .findAny())
        .describedAs("Expect that sub process is activated")
        .isPresent();
  }

  @Test
  public void shouldMigrateProcessInstanceToACompletelyNewProcessDefinition() {
    // given
    final String processId = helper.getBpmnProcessId();
    final long definitionKey =
        CLIENT_RULE.deployProcess(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .endEvent()
                .done());
    final long processInstanceKey = CLIENT_RULE.createProcessInstance(definitionKey);

    final long serviceTaskKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst()
            .getKey();

    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("A")
            .getFirst()
            .getKey();

    final String targetProcessId = helper.getBpmnProcessId() + "1";
    final long targetProcessDefinitionKey =
        CLIENT_RULE.deployProcess(
            Bpmn.createExecutableProcess(targetProcessId)
                .startEvent()
                .serviceTask("B", a -> a.zeebeJobType("B"))
                .endEvent("end")
                .done());

    // when
    final var command =
        CLIENT_RULE
            .getClient()
            .newMigrateProcessInstanceCommand(processInstanceKey)
            .migrationPlan(targetProcessDefinitionKey)
            .addMappingInstruction("A", "B")
            .send();

    // then
    assertThatNoException()
        .describedAs("Expect that migration command is not rejected")
        .isThrownBy(command::join);

    final var migratedProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue();

    final var migratedTaskA =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
            .withRecordKey(serviceTaskKey)
            .getFirst()
            .getValue();

    final var migratedJobA =
        RecordingExporter.jobRecords(JobIntent.MIGRATED)
            .withRecordKey(jobKey)
            .getFirst()
            .getValue();

    assertThat(migratedProcessInstance)
        .describedAs("Expect that process definition key changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId(targetProcessId)
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    assertThat(migratedTaskA)
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasVersion(1)
        .describedAs("Expect that element id changed due to mapping")
        .hasElementId("B");

    assertThat(migratedJobA)
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasProcessDefinitionVersion(1)
        .describedAs("Expect that element id changed due to mapping")
        .hasElementId("B")
        .describedAs(
            "Expect that the type did not change even though it's different in the target process."
                + " Re-evaluation of the job type expression is not enabled for this migration")
        .hasType("A");

    CLIENT_RULE.getClient().newCompleteCommand(jobKey).send().join();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withRecordKey(serviceTaskKey)
        .await();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("end")
                .findAny())
        .describedAs("Expect that end event is activated")
        .isPresent();
  }
}
