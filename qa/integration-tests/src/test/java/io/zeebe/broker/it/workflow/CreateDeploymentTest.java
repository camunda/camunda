/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.command.ClientException;
import io.zeebe.client.api.response.DeploymentEvent;
import io.zeebe.client.api.response.Workflow;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CreateDeploymentTest {
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldDeployWorkflowModel() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
    // when
    final DeploymentEvent result =
        clientRule
            .getClient()
            .newDeployCommand()
            .addWorkflowModel(workflow, "workflow.bpmn")
            .send()
            .join();

    // then
    assertThat(result.getKey()).isGreaterThan(0);
    assertThat(result.getWorkflows()).hasSize(1);

    final Workflow deployedWorkflow = result.getWorkflows().get(0);
    assertThat(deployedWorkflow.getBpmnProcessId()).isEqualTo("process");
    assertThat(deployedWorkflow.getVersion()).isEqualTo(1);
    assertThat(deployedWorkflow.getWorkflowKey())
        .isEqualTo(Protocol.encodePartitionId(Protocol.DEPLOYMENT_PARTITION, 1L));
    assertThat(deployedWorkflow.getResourceName()).isEqualTo("workflow.bpmn");
  }

  @Test
  public void shouldNotDeployUnparsableModel() {
    // then
    exception.expect(ClientException.class);
    exception.expectMessage("'invalid.bpmn': SAXException while parsing input stream");

    // when
    clientRule
        .getClient()
        .newDeployCommand()
        .addResourceStringUtf8("Foooo", "invalid.bpmn")
        .send()
        .join();
  }

  @Test
  public void shouldNotDeployInvalidModel() throws Exception {
    // then
    exception.expect(ClientException.class);
    exception.expectMessage("Must have at least one start event");

    // when
    clientRule
        .getClient()
        .newDeployCommand()
        .addResourceFile(
            getClass()
                .getResource("/workflows/invalid_process.bpmn")
                .getFile()) // does not have a start event
        .send()
        .join();
  }

  @Test
  public void shouldNotDeployNonExecutableModel() {
    // given
    final BpmnModelInstance model =
        Bpmn.createProcess("not-executable").startEvent().endEvent().done();

    // then
    exception.expect(ClientException.class);
    exception.expectMessage("Must contain at least one executable process");

    // when
    clientRule
        .getClient()
        .newDeployCommand()
        .addWorkflowModel(model, "workflow.bpmn")
        .send()
        .join();
  }

  @Test
  public void shouldDeployYamlWorkflow() {
    // when
    final DeploymentEvent result =
        clientRule
            .getClient()
            .newDeployCommand()
            .addResourceFromClasspath("workflows/simple-workflow.yaml")
            .send()
            .join();

    // then
    assertThat(result.getKey()).isGreaterThan(0);
    assertThat(result.getWorkflows()).hasSize(1);

    final Workflow deployedWorkflow = result.getWorkflows().get(0);
    assertThat(deployedWorkflow.getBpmnProcessId()).isEqualTo("yaml-workflow");
    assertThat(deployedWorkflow.getVersion()).isEqualTo(1);
    assertThat(deployedWorkflow.getWorkflowKey()).isGreaterThan(0);
  }
}
