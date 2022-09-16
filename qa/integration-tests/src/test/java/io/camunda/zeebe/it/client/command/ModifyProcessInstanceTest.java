/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ModifyProcessInstanceTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();
  private String processId;
  private String processId2;
  private long processDefinitionKey;

  @Before
  public void deploy() {
    processId = helper.getBpmnProcessId();
    processId2 = helper.getBpmnProcessId();
    processDefinitionKey =
        CLIENT_RULE.deployProcess(
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done());
    CLIENT_RULE.deployProcess(
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

  @Test
  public void shouldModifyExistingProcessInstance() {
    // given
    final var processInstance =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId2)
            .latestVersion()
            .send()
            .join();
    final var processInstanceKey = processInstance.getProcessInstanceKey();

    final var activatedUserTask =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();

    // when
    final var command =
        CLIENT_RULE
            .getClient()
            .newModifyProcessInstanceCommand(processInstanceKey)
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

  @Test
  public void shouldRejectCommandWhenProcessInstanceUnknown() {
    // when
    final var command =
        CLIENT_RULE
            .getClient()
            .newModifyProcessInstanceCommand(
                processDefinitionKey) // needs to be a valid key since we extract the partition from
            // it
            .activateElement("element")
            .send();

    // then
    assertThatThrownBy(command::join)
        .isInstanceOf(ClientStatusException.class)
        .hasMessageContaining(
            String.format(
                "Expected to modify process instance but no process instance found with key '%d'",
                processDefinitionKey));
  }

  @Test
  public void shouldRejectCommandForUnknownTerminationTarget() {
    // given
    final var processInstance =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId2)
            .latestVersion()
            .send()
            .join();

    // when
    final var command =
        CLIENT_RULE
            .getClient()
            .newModifyProcessInstanceCommand(processInstance.getProcessInstanceKey())
            .terminateElement(123)
            .send();

    // then
    assertThatThrownBy(command::join)
        .isInstanceOf(ClientStatusException.class)
        .hasMessageContaining(
            """
            Expected to modify instance of process \
            'process-shouldRejectCommandForUnknownTerminationTarget' but it contains one or \
            more terminate instructions with an element instance that could not be found: \
            '123'""");
  }
}
