/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.client.impl.util.DataSizeUtil.ONE_MB;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.util.ByteValue;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.springframework.util.unit.DataSize;

public final class CreateProcessInstanceWithLargeResultTest {

  private static final EmbeddedBrokerRule BROKER_RULE =
      new EmbeddedBrokerRule(
          cfg -> {
            cfg.getNetwork().setMaxMessageSize(DataSize.ofMegabytes(21));
            cfg.getGateway().getNetwork().setMaxMessageSize(DataSize.ofMegabytes(21));
          });
  private static final GrpcClientRule CLIENT_RULE =
      new GrpcClientRule(
          BROKER_RULE, zeebeClientBuilder -> zeebeClientBuilder.maxMessageSize(21 * ONE_MB));

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldCreateInstanceWithLargeResult() {
    // given
    CLIENT_RULE.deployProcess(
        Bpmn.createExecutableProcess("PROCESS").startEvent().endEvent().done());

    // when
    final ProcessInstanceResult processInstance =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("PROCESS")
            .latestVersion()
            .variables(Map.of("variable", "x".repeat((int) (ByteValue.ofMegabytes(10)))))
            .withResult()
            .send()
            .join();

    // then
    assertThat(processInstance.getVariables().getBytes(StandardCharsets.UTF_8))
        .hasSizeGreaterThan((int) ByteValue.ofMegabytes(10));
  }
}
