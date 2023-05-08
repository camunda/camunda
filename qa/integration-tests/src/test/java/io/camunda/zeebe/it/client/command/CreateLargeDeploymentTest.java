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
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.springframework.util.unit.DataSize;

public final class CreateLargeDeploymentTest {

  private static final EmbeddedBrokerRule BROKER_RULE =
      new EmbeddedBrokerRule(b -> b.getNetwork().setMaxMessageSize(DataSize.ofMegabytes(1)));
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  // Regression "https://github.com/camunda/zeebe/issues/12591")
  @Test
  public void shouldRejectDeployIfResourceIsTooLarge() {
    // when
    final var deployLargeProcess =
        CLIENT_RULE
            .getClient()
            .newDeployResourceCommand()
            .addResourceFromClasspath("processes/too_large_process.bpmn")
            .send();

    // then
    assertThatThrownBy(deployLargeProcess::join)
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Request size is above configured maxMessageSize.");

    // then - can deploy another process
    final var deployedValidProcess =
        CLIENT_RULE
            .getClient()
            .newDeployResourceCommand()
            .addResourceFromClasspath("processes/one-task-process.bpmn")
            .send()
            .join();
    assertThat(deployedValidProcess.getProcesses()).hasSize(1);
  }
}
