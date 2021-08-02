/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system;

import static io.camunda.zeebe.broker.test.EmbeddedBrokerConfigurator.setPartitionCount;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.test.broker.protocol.commandapi.CommandApiRule;
import io.camunda.zeebe.test.util.TestUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class UniqueKeyFormatTest {

  public final EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(setPartitionCount(3));
  public final CommandApiRule apiRule = new CommandApiRule(brokerRule::getAtomixCluster);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  // todo: this does not need to be an integration test if the only test is that the partitionId is
  // encoded in the key
  @Test
  public void shouldStartProcessInstanceAtNoneStartEvent() {
    // given
    apiRule
        .partitionClient()
        .deploy(Bpmn.createExecutableProcess("process").startEvent("foo").endEvent().done());

    // when
    TestUtil.waitUntil(() -> RecordingExporter.deploymentRecords().withPartitionId(2).exists());
    final ProcessInstanceCreationRecord processInstanceWithResponse =
        apiRule.partitionClient(2).createProcessInstance(r -> r.setBpmnProcessId("process"));

    // then partition id is encoded in the returned getKey
    final long key = processInstanceWithResponse.getProcessInstanceKey();
    final int partitionId = Protocol.decodePartitionId(key);
    assertThat(partitionId).isEqualTo(2);
  }
}
