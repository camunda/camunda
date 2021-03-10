/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system;

import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setPartitionCount;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.zeebe.test.broker.protocol.commandapi.CommandApiRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.test.util.record.RecordingExporter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class UniqueKeyFormatTest {

  public final EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(setPartitionCount(3));
  public final CommandApiRule apiRule = new CommandApiRule(brokerRule::getAtomix);

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
