/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.TestUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class ClientReconnectTest {

  public final EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public final GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);
  private long processDefinitionKey;

  @Before
  public void init() {
    processDefinitionKey =
        clientRule.deployProcess(
            Bpmn.createExecutableProcess("process").startEvent().endEvent().done());
  }

  @Test
  public void shouldTransparentlyReconnectOnUnexpectedConnectionLoss() {
    // given
    createProcessInstance(processDefinitionKey);

    // when
    brokerRule.stopBroker();

    assertThatThrownBy(() -> createProcessInstance(processDefinitionKey))
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("io exception");

    brokerRule.startBroker();

    TestUtil.doRepeatedly(
            () -> {
              try {
                return createProcessInstance(processDefinitionKey);
              } catch (final ClientException e) {
                // ignore failures until broker is up again
                return -1L;
              }
            })
        .until(key -> key > 0);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .filterRootScope()
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(2)
                .count())
        .isEqualTo(2);
  }

  private long createProcessInstance(final long processDefinitionKey) {
    return clientRule
        .getClient()
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .send()
        .join()
        .getProcessInstanceKey();
  }
}
