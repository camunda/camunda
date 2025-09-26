/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ZeebeIntegration
public class ModifyProcessInstanceTest {

  @AutoClose CamundaClient client;

  @TestZeebe
  final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  ZeebeResourcesHelper resourcesHelper;
  private String processId;
  private String processId2;
  private long processDefinitionKey;

  @BeforeEach
  public void init() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldModifyExistingProcessInstance(final boolean useRest, final TestInfo testInfo) {
    // given
    deploy(testInfo);
    final var processInstance =
        client.newCreateInstanceCommand().bpmnProcessId(processId2).latestVersion().send().join();
    final var processInstanceKey = processInstance.getProcessInstanceKey();

    final var activatedUserTask =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();

    // when
    final var command =
        getCommand(client, useRest, processInstanceKey)
            .activateElement("B")
            .withVariables(Map.of("foo", "bar"), "B")
            .and()
            .activateElement("C")
            .withVariables(Map.of("fizz", "buzz"), "C")
            .and()
            .terminateElement(activatedUserTask.getKey())
            .send();

    // then
    assertThatNoException()
        .describedAs("Expect that modification command is not rejected")
        .isThrownBy(command::join);

    final var terminatedTaskA =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
            .withRecordKey(activatedUserTask.getKey())
            .withElementId("A")
            .findAny();
    final var activatedTaskB =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("B")
            .findAny();
    final var activatedTaskC =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("C")
            .findAny();

    Assertions.assertThat(terminatedTaskA)
        .describedAs("Expect that task A is terminated")
        .isPresent();
    Assertions.assertThat(activatedTaskB)
        .describedAs("Expect that task B is activated")
        .isPresent();
    Assertions.assertThat(activatedTaskC)
        .describedAs("Expect that task C is activated")
        .isPresent();

    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(activatedTaskB.get().getKey())
                .withName("foo")
                .getFirst())
        .describedAs("Expect that variable 'foo' is created in task B's scope")
        .isNotNull()
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getValue)
        .describedAs("Expect that variable is created with value '\"bar\"'")
        .isEqualTo("\"bar\"");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldModifyExistingProcessInstanceWithSingleVariable(
      final boolean useRest, final TestInfo testInfo) {
    // given
    deploy(testInfo);
    final var processInstance =
        client.newCreateInstanceCommand().bpmnProcessId(processId2).latestVersion().send().join();
    final var processInstanceKey = processInstance.getProcessInstanceKey();

    final var activatedUserTask =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();

    // when
    final var command =
        getCommand(client, useRest, processInstanceKey)
            .activateElement("B")
            .withVariable("foo", "bar", "B")
            .and()
            .activateElement("C")
            .withVariable("fizz", "buzz", "C")
            .and()
            .terminateElement(activatedUserTask.getKey())
            .send();

    // then
    assertThatNoException()
        .describedAs("Expect that modification command is not rejected")
        .isThrownBy(command::join);

    final var terminatedTaskA =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
            .withRecordKey(activatedUserTask.getKey())
            .withElementId("A")
            .findAny();
    final var activatedTaskB =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("B")
            .findAny();
    final var activatedTaskC =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("C")
            .findAny();

    Assertions.assertThat(terminatedTaskA)
        .describedAs("Expect that task A is terminated")
        .isPresent();
    Assertions.assertThat(activatedTaskB)
        .describedAs("Expect that task B is activated")
        .isPresent();
    Assertions.assertThat(activatedTaskC)
        .describedAs("Expect that task C is activated")
        .isPresent();

    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(activatedTaskB.get().getKey())
                .withName("foo")
                .getFirst())
        .describedAs("Expect that variable 'foo' is created in task B's scope")
        .isNotNull()
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getValue)
        .describedAs("Expect that variable is created with value '\"bar\"'")
        .isEqualTo("\"bar\"");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectCommandWhenProcessInstanceUnknown(
      final boolean useRest, final TestInfo testInfo) {
    // when
    deploy(testInfo);
    final var command =
        getCommand(client, useRest, processDefinitionKey).activateElement("element").send();

    // then
    if (useRest) {
      assertThatThrownBy(command::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining(
              String.format(
                  "Expected to modify process instance but no process instance found with key '%d'",
                  processDefinitionKey));
    } else {
      assertThatThrownBy(command::join)
          .isInstanceOf(ClientStatusException.class)
          .hasMessageContaining(
              String.format(
                  "Expected to modify process instance but no process instance found with key '%d'",
                  processDefinitionKey));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectCommandForUnknownTerminationTarget(
      final boolean useRest, final TestInfo testInfo) {
    // given
    deploy(testInfo);
    final var processInstance =
        client.newCreateInstanceCommand().bpmnProcessId(processId2).latestVersion().send().join();

    // when
    final var command =
        getCommand(client, useRest, processInstance.getProcessInstanceKey())
            .terminateElement(123)
            .send();

    // then
    assertThatThrownBy(command::join)
        .hasMessageContaining(
            """
            Expected to modify instance of process \
            'process-shouldRejectCommandForUnknownTerminationTarget-2' but it contains one or \
            more terminate instructions with an element instance that could not be found: \
            '123'""");
  }

  private ModifyProcessInstanceCommandStep1 getCommand(
      final CamundaClient client, final boolean useRest, final long processInstanceKey) {
    final ModifyProcessInstanceCommandStep1 modifyCommand =
        client.newModifyProcessInstanceCommand(processInstanceKey);
    return useRest ? modifyCommand.useRest() : modifyCommand.useGrpc();
  }

  private void deploy(final TestInfo testInfo) {
    processId = "process-" + testInfo.getTestMethod().get().getName();
    processId2 = processId + "-2";
    processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done());
    resourcesHelper.deployProcess(
        Bpmn.createExecutableProcess(processId2)
            .startEvent()
            .userTask("A")
            .parallelGateway()
            .userTask("B")
            .moveToLastGateway()
            .userTask("C")
            .endEvent()
            .done());
  }
}
