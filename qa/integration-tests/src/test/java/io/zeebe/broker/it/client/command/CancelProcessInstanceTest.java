/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.it.client.command;

import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertProcessInstanceCanceled;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.Status.Code;
import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.command.ClientStatusException;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.test.util.BrokerClassRuleHelper;
import io.zeebe.test.util.record.RecordingExporter;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class CancelProcessInstanceTest {

  private static final String PROCESS_ID = "process";

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private long processDefinitionKey;

  @Before
  public void init() {
    processDefinitionKey =
        CLIENT_RULE.deployProcess(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .done());
  }

  @Test
  public void shouldCancelProcessInstance() {
    // given
    final long processInstanceKey = CLIENT_RULE.createProcessInstance(processDefinitionKey);

    // when
    CLIENT_RULE.getClient().newCancelInstanceCommand(processInstanceKey).send().join();

    // then
    assertProcessInstanceCanceled(PROCESS_ID);
  }

  @Test
  public void shouldRejectIfProcessInstanceIsEnded() {
    // given
    final long processInstanceKey = CLIENT_RULE.createProcessInstance(processDefinitionKey);

    CLIENT_RULE.getClient().newCancelInstanceCommand(processInstanceKey).send().join();

    // when
    final var command = CLIENT_RULE.getClient().newCancelInstanceCommand(processInstanceKey).send();

    // then
    assertThatThrownBy(() -> command.join())
        .isInstanceOf(ClientStatusException.class)
        .extracting("status.code")
        .isEqualTo(Code.NOT_FOUND);
  }

  @Test
  public void shouldRejectIfNotProcessInstanceKey() {
    // given
    final long processInstanceKey = CLIENT_RULE.createProcessInstance(processDefinitionKey);

    final long elementInstanceKey =
        RecordingExporter.jobRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue()
            .getElementInstanceKey();

    // when
    final var command = CLIENT_RULE.getClient().newCancelInstanceCommand(elementInstanceKey).send();

    // then
    assertThatThrownBy(() -> command.join())
        .isInstanceOf(ClientStatusException.class)
        .extracting("status.code")
        .isEqualTo(Code.NOT_FOUND);
  }
}
