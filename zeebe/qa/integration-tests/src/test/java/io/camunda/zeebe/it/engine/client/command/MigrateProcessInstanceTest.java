/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.MigrateProcessInstanceCommandStep1;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ZeebeIntegration
public class MigrateProcessInstanceTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose CamundaClient client;
  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  public void init() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldMigrateProcessInstanceWithToASmallDifferenceProcessDefinition(
      final boolean useRest, final TestInfo testInfo) {
    // given
    final String processId = "process-" + testInfo.getTestMethod().get().getName();
    final long definitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .endEvent()
                .done());
    final long processInstanceKey = resourcesHelper.createProcessInstance(definitionKey);

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
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("B", a -> a.zeebeJobType("B"))
                .endEvent("end")
                .done());

    // when
    final var command =
        getCommand(client, useRest, processInstanceKey)
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

    client.newCompleteCommand(jobKey).send().join();
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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldMigrateProcessInstanceWithToALargeDifferenceProcessDefinition(
      final boolean useRest, final TestInfo testInfo) {
    // given
    final String processId = "process-" + testInfo.getTestMethod().get().getName();
    final long definitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .endEvent()
                .done());
    final long processInstanceKey = resourcesHelper.createProcessInstance(definitionKey);

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
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("U")
                .serviceTask("B", a -> a.zeebeJobType("B"))
                .subProcess("sub", s -> s.embeddedSubProcess().startEvent().endEvent())
                .endEvent()
                .done());

    // when
    final var command =
        getCommand(client, useRest, processInstanceKey)
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

    client.newCompleteCommand(jobKey).send().join();
    RecordingExporter.jobRecords(JobIntent.COMPLETED).withRecordKey(jobKey).await();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("sub")
                .findAny())
        .describedAs("Expect that sub process is activated")
        .isPresent();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldMigrateProcessInstanceToACompletelyNewProcessDefinition(
      final boolean useRest, final TestInfo testInfo) {
    // given
    final String processId = "process-" + testInfo.getTestMethod().get().getName();
    final long definitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .endEvent()
                .done());
    final long processInstanceKey = resourcesHelper.createProcessInstance(definitionKey);

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

    final String targetProcessId = processId + "1";
    final long targetProcessDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess(targetProcessId)
                .startEvent()
                .serviceTask("B", a -> a.zeebeJobType("B"))
                .endEvent("end")
                .done());

    // when
    final var command =
        getCommand(client, useRest, processInstanceKey)
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

    client.newCompleteCommand(jobKey).send().join();
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

  private MigrateProcessInstanceCommandStep1 getCommand(
      final CamundaClient client, final boolean useRest, final long processInstanceKey) {
    final MigrateProcessInstanceCommandStep1 migrateCommand =
        client.newMigrateProcessInstanceCommand(processInstanceKey);
    return useRest ? migrateCommand.useRest() : migrateCommand.useGrpc();
  }
}
