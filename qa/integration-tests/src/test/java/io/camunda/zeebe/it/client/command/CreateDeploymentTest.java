/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class CreateDeploymentTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldDeployProcessModel() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String resourceName = processId + ".bpmn";

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .endEvent()
            .done();

    // when
    final DeploymentEvent result =
        CLIENT_RULE
            .getClient()
            .newDeployResourceCommand()
            .addProcessModel(process, resourceName)
            .send()
            .join();

    // then
    assertThat(result.getKey()).isGreaterThan(0);
    assertThat(result.getProcesses()).hasSize(1);

    final Process deployedProcess = result.getProcesses().get(0);
    assertThat(deployedProcess.getBpmnProcessId()).isEqualTo(processId);
    assertThat(deployedProcess.getVersion()).isEqualTo(1);
    assertThat(deployedProcess.getProcessDefinitionKey()).isGreaterThan(0);
    assertThat(deployedProcess.getResourceName()).isEqualTo(resourceName);
  }

  @Test
  public void shouldDeployDecisionModel() {
    // given
    final String resourceName = "dmn/drg-force-user.dmn";

    // when
    final DeploymentEvent result =
        CLIENT_RULE
            .getClient()
            .newDeployResourceCommand()
            .addResourceFromClasspath(resourceName)
            .send()
            .join();

    // then
    assertThat(result.getKey()).isPositive();
    assertThat(result.getDecisionRequirements()).hasSize(1);
    assertThat(result.getDecisions()).hasSize(2);

    final var decisionRequirements = result.getDecisionRequirements().get(0);
    assertThat(decisionRequirements.getDmnDecisionRequirementsId()).isEqualTo("force_users");
    assertThat(decisionRequirements.getDmnDecisionRequirementsName()).isEqualTo("Force Users");
    assertThat(decisionRequirements.getVersion()).isEqualTo(1);
    assertThat(decisionRequirements.getDecisionRequirementsKey()).isPositive();
    assertThat(decisionRequirements.getResourceName()).isEqualTo(resourceName);

    final var decision1 = result.getDecisions().get(0);
    assertThat(decision1.getDmnDecisionId()).isEqualTo("jedi_or_sith");
    assertThat(decision1.getDmnDecisionName()).isEqualTo("Jedi or Sith");
    assertThat(decision1.getVersion()).isEqualTo(1);
    assertThat(decision1.getDecisionKey()).isPositive();
    assertThat(decision1.getDmnDecisionRequirementsId()).isEqualTo("force_users");
    assertThat(decision1.getDecisionRequirementsKey()).isPositive();

    final var decision2 = result.getDecisions().get(1);
    assertThat(decision2.getDmnDecisionId()).isEqualTo("force_user");
    assertThat(decision2.getDmnDecisionName()).isEqualTo("Which force user?");
    assertThat(decision2.getVersion()).isEqualTo(1);
    assertThat(decision2.getDecisionKey()).isPositive();
    assertThat(decision2.getDmnDecisionRequirementsId()).isEqualTo("force_users");
    assertThat(decision2.getDecisionRequirementsKey()).isPositive();
  }

  @Test
  public void shouldRejectDeployIfProcessIsInvalid() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process").startEvent().serviceTask("task").done();

    // when
    final var command =
        CLIENT_RULE
            .getClient()
            .newDeployResourceCommand()
            .addProcessModel(process, "process.bpmn")
            .send();

    // when
    assertThatThrownBy(() -> command.join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Must have exactly one 'zeebe:taskDefinition' extension element");
  }
}
