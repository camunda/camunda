/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.command.ClientException;
import io.zeebe.client.api.response.WorkflowInstanceEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.test.util.BrokerClassRuleHelper;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class CreateWorkflowInstanceTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private String processId;
  private long firstWorkflowKey;
  private Long secondWorkflowKey;

  @Before
  public void deployProcess() {
    processId = helper.getBpmnProcessId();

    firstWorkflowKey =
        CLIENT_RULE.deployWorkflow(Bpmn.createExecutableProcess(processId).startEvent("v1").done());
    secondWorkflowKey =
        CLIENT_RULE.deployWorkflow(Bpmn.createExecutableProcess(processId).startEvent("v2").done());
  }

  @Test
  public void shouldCreateBpmnProcessById() {
    // when
    final WorkflowInstanceEvent workflowInstance =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join();

    // then
    assertThat(workflowInstance.getBpmnProcessId()).isEqualTo(processId);
    assertThat(workflowInstance.getVersion()).isEqualTo(2);
    assertThat(workflowInstance.getWorkflowKey()).isEqualTo(secondWorkflowKey);
  }

  @Test
  public void shouldCreateBpmnProcessByIdAndVersion() {
    // when
    final WorkflowInstanceEvent workflowInstance =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .version(1)
            .send()
            .join();

    // then instance is created of first workflow version
    assertThat(workflowInstance.getBpmnProcessId()).isEqualTo(processId);
    assertThat(workflowInstance.getVersion()).isEqualTo(1);
    assertThat(workflowInstance.getWorkflowKey()).isEqualTo(firstWorkflowKey);
  }

  @Test
  public void shouldCreateBpmnProcessByKey() {
    // when
    final WorkflowInstanceEvent workflowInstance =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .workflowKey(firstWorkflowKey)
            .send()
            .join();

    // then
    assertThat(workflowInstance.getBpmnProcessId()).isEqualTo(processId);
    assertThat(workflowInstance.getVersion()).isEqualTo(1);
    assertThat(workflowInstance.getWorkflowKey()).isEqualTo(firstWorkflowKey);
  }

  @Test
  public void shouldCreateWithVariables() {
    // given
    final Map<String, Object> variables = Map.of("foo", 123);

    // when
    final WorkflowInstanceEvent event =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    // then
    final var createdEvent =
        RecordingExporter.workflowInstanceCreationRecords()
            .withIntent(WorkflowInstanceCreationIntent.CREATED)
            .withInstanceKey(event.getWorkflowInstanceKey())
            .getFirst();

    assertThat(createdEvent.getValue().getVariables()).containsExactlyEntriesOf(variables);
  }

  @Test
  public void shouldCreateWithoutVariables() {
    // when
    final WorkflowInstanceEvent event =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join();

    // then
    final var createdEvent =
        RecordingExporter.workflowInstanceCreationRecords()
            .withIntent(WorkflowInstanceCreationIntent.CREATED)
            .withInstanceKey(event.getWorkflowInstanceKey())
            .getFirst();

    assertThat(createdEvent.getValue().getVariables()).isEmpty();
  }

  @Test
  public void shouldCreateWithNullVariables() {
    // when
    final WorkflowInstanceEvent event =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variables("null")
            .send()
            .join();

    // then
    final var createdEvent =
        RecordingExporter.workflowInstanceCreationRecords()
            .withIntent(WorkflowInstanceCreationIntent.CREATED)
            .withInstanceKey(event.getWorkflowInstanceKey())
            .getFirst();

    assertThat(createdEvent.getValue().getVariables()).isEmpty();
  }

  @Test
  public void shouldRejectCompleteJobIfVariablesAreInvalid() {
    // when
    final var command =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variables("[]")
            .send();

    assertThatThrownBy(() -> command.join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining(
            "Property 'variables' is invalid: Expected document to be a root level object, but was 'ARRAY'");
  }

  @Test
  public void shouldRejectCreateBpmnProcessByNonExistingId() {
    // when
    final var command =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("non-existing")
            .latestVersion()
            .send();

    assertThatThrownBy(() -> command.join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining(
            "Expected to find workflow definition with process ID 'non-existing', but none found");
  }

  @Test
  public void shouldRejectCreateBpmnProcessByNonExistingKey() {
    // when
    final var command = CLIENT_RULE.getClient().newCreateInstanceCommand().workflowKey(123L).send();

    assertThatThrownBy(() -> command.join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining(
            "Expected to find workflow definition with key '123', but none found");
  }
}
