/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
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
  private long processDefinitionKey;

  @Before
  public void deploy() {
    processId = helper.getBpmnProcessId();
    processDefinitionKey =
        CLIENT_RULE.deployProcess(
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done());
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
    assertThatThrownBy(() -> command.join())
        .isInstanceOf(ClientStatusException.class)
        .hasMessageContaining(
            String.format(
                "Expected to modify process instance but no process instance found with key '%d'",
                processDefinitionKey));
  }
}
