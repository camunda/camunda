/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.command.ClientException;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.TestUtil;
import io.zeebe.test.util.record.RecordingExporter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class ClientReconnectTest {

  public final EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public final GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);
  private long workflowKey;

  @Before
  public void init() {
    workflowKey =
        clientRule.deployWorkflow(
            Bpmn.createExecutableProcess("process").startEvent().endEvent().done());
  }

  @Test
  public void shouldTransparentlyReconnectOnUnexpectedConnectionLoss() {
    // given
    createWorkflowInstance(workflowKey);

    // when
    brokerRule.stopBroker();

    assertThatThrownBy(() -> createWorkflowInstance(workflowKey))
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("io exception");

    brokerRule.startBroker();

    TestUtil.doRepeatedly(
            () -> {
              try {
                return createWorkflowInstance(workflowKey);
              } catch (final ClientException e) {
                // ignore failures until broker is up again
                return -1L;
              }
            })
        .until(key -> key > 0);

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
                .filterRootScope()
                .withWorkflowKey(workflowKey)
                .limit(2)
                .count())
        .isEqualTo(2);
  }

  private long createWorkflowInstance(final long workflowKey) {
    return clientRule
        .getClient()
        .newCreateInstanceCommand()
        .workflowKey(workflowKey)
        .send()
        .join()
        .getWorkflowInstanceKey();
  }
}
