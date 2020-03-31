/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.client.command;

import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCanceled;
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

public final class CancelWorkflowInstanceTest {

  private static final String PROCESS_ID = "process";

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private long workflowKey;

  @Before
  public void init() {
    workflowKey =
        CLIENT_RULE.deployWorkflow(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .done());
  }

  @Test
  public void shouldCancelWorkflowInstance() {
    // given
    final long workflowInstanceKey = CLIENT_RULE.createWorkflowInstance(workflowKey);

    // when
    CLIENT_RULE.getClient().newCancelInstanceCommand(workflowInstanceKey).send().join();

    // then
    assertWorkflowInstanceCanceled(PROCESS_ID);
  }

  @Test
  public void shouldRejectIfWorkflowInstanceIsEnded() {
    // given
    final long workflowInstanceKey = CLIENT_RULE.createWorkflowInstance(workflowKey);

    CLIENT_RULE.getClient().newCancelInstanceCommand(workflowInstanceKey).send().join();

    // when
    final var command =
        CLIENT_RULE.getClient().newCancelInstanceCommand(workflowInstanceKey).send();

    // then
    assertThatThrownBy(() -> command.join())
        .isInstanceOf(ClientStatusException.class)
        .extracting("status.code")
        .isEqualTo(Code.NOT_FOUND);
  }

  @Test
  public void shouldRejectIfNotWorkflowInstanceKey() {
    // given
    final long workflowInstanceKey = CLIENT_RULE.createWorkflowInstance(workflowKey);

    final long elementInstanceKey =
        RecordingExporter.jobRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
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
