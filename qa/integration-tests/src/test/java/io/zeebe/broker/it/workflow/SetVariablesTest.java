/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.workflow;

import static io.zeebe.broker.it.util.StatusCodeMatcher.hasStatusCode;
import static io.zeebe.broker.it.util.StatusDescriptionMatcher.descriptionContains;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertElementInState;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertVariableDocumentUpdated;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCompleted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.grpc.Status.Code;
import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.command.ClientStatusException;
import io.zeebe.client.api.response.DeploymentEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.test.util.record.WorkflowInstances;
import java.util.Collections;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class SetVariablesTest {
  private static final String VARIABLES = "{\"foo\":\"bar\"}";

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process")
          .startEvent("start")
          .serviceTask("task-1", t -> t.zeebeTaskType("task-1").zeebeOutput("result", "result"))
          .endEvent("end")
          .done();

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static final RuleChain RULE_CHAIN = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public ExpectedException thrown = ExpectedException.none();

  @BeforeClass
  public static void init() {
    final DeploymentEvent deploymentEvent =
        CLIENT_RULE
            .getClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

    CLIENT_RULE.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }

  @Test
  public void shouldSetVariablesWhenActivityIsActivated() {
    // given
    final long workflowInstanceKey = createWorkflowInstanceAndAwaitTaskActivation();

    // when
    CLIENT_RULE
        .getClient()
        .newSetVariablesCommand(workflowInstanceKey)
        .variables(VARIABLES)
        .send()
        .join();

    // then
    assertVariableDocumentUpdated(
        (variableDocument) ->
            assertThat(variableDocument.getVariables()).containsOnly(entry("foo", "bar")));
  }

  @Test
  public void shouldSetVariablesWithNullVariables() {
    // given
    final long workflowInstanceKey = createWorkflowInstanceAndAwaitTaskActivation();

    // when
    CLIENT_RULE
        .getClient()
        .newSetVariablesCommand(workflowInstanceKey)
        .variables("null")
        .send()
        .join();

    // then
    assertVariableDocumentUpdated(
        (variableDocument) -> assertThat(variableDocument.getVariables()).isEmpty());
  }

  @Test
  public void shouldThrowExceptionOnSetVariablesWithInvalidVariables() {
    // given
    final long workflowInstanceKey = createWorkflowInstanceAndAwaitTaskActivation();

    // expect
    thrown.expect(ClientStatusException.class);
    thrown.expect(hasStatusCode(Code.INVALID_ARGUMENT));
    thrown.expect(
        descriptionContains(
            "Property 'variables' is invalid: Expected document to be a root level object, but was 'ARRAY'"));

    // when
    CLIENT_RULE
        .getClient()
        .newSetVariablesCommand(workflowInstanceKey)
        .variables("[]")
        .send()
        .join();
  }

  @Test
  public void shouldSetVariablesAndCompleteJobAfterwards() {
    // given
    final long workflowInstanceKey = createWorkflowInstanceAndAwaitTaskActivation();

    // when
    CLIENT_RULE
        .getClient()
        .newSetVariablesCommand(workflowInstanceKey)
        .variables(VARIABLES)
        .send()
        .join();
    assertVariableDocumentUpdated();

    CLIENT_RULE
        .getClient()
        .newWorker()
        .jobType("task-1")
        .handler(
            (client, job) ->
                client.newCompleteCommand(job.getKey()).variables("{\"result\": \"ok\"}").send())
        .open();

    // then
    assertWorkflowInstanceCompleted(workflowInstanceKey);
    assertThat(WorkflowInstances.getCurrentVariables(workflowInstanceKey))
        .containsOnly(entry("foo", "\"bar\""), entry("result", "\"ok\""));
  }

  @Test
  public void shouldSetVariablesWithMap() {
    // given
    final long workflowInstanceKey = createWorkflowInstanceAndAwaitTaskActivation();

    // when
    CLIENT_RULE
        .getClient()
        .newSetVariablesCommand(workflowInstanceKey)
        .variables(Collections.singletonMap("foo", "bar"))
        .send()
        .join();

    // then
    assertVariableDocumentUpdated(
        (variableDocument) ->
            assertThat(variableDocument.getVariables()).containsOnly(entry("foo", "bar")));
  }

  @Test
  public void shouldSetVariablesWithObject() {
    // given
    final long workflowInstanceKey = createWorkflowInstanceAndAwaitTaskActivation();
    final VariableDocument newVariables = new VariableDocument();
    newVariables.foo = "bar";

    // when
    CLIENT_RULE
        .getClient()
        .newSetVariablesCommand(workflowInstanceKey)
        .variables(newVariables)
        .send()
        .join();

    // then
    assertVariableDocumentUpdated(
        (variableDocument) ->
            assertThat(variableDocument.getVariables()).containsOnly(entry("foo", "bar")));
  }

  @Test
  public void shouldFailSetVariablesIfWorkflowInstanceIsCompleted() {
    // given
    final long workflowInstanceKey = createWorkflowInstanceAndAwaitTaskActivation();
    CLIENT_RULE
        .getClient()
        .newWorker()
        .jobType("task-1")
        .handler(
            (client, job) ->
                client.newCompleteCommand(job.getKey()).variables("{\"result\": \"done\"}").send())
        .open();
    assertWorkflowInstanceCompleted("process", workflowInstanceKey);

    // then
    thrown.expect(ClientStatusException.class);
    thrown.expect(hasStatusCode(Code.NOT_FOUND));

    // when
    CLIENT_RULE
        .getClient()
        .newSetVariablesCommand(workflowInstanceKey)
        .variables(VARIABLES)
        .send()
        .join();
  }

  private long createWorkflowInstanceAndAwaitTaskActivation() {
    final long workflowInstanceKey =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join()
            .getWorkflowInstanceKey();
    assertElementInState(workflowInstanceKey, "task-1", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    return workflowInstanceKey;
  }

  public static class VariableDocument {
    public String foo;
  }
}
