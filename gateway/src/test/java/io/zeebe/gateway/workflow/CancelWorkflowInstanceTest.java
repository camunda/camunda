/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.events.WorkflowInstanceEvent;
import io.zeebe.gateway.api.events.WorkflowInstanceState;
import io.zeebe.gateway.cmd.ClientCommandRejectedException;
import io.zeebe.gateway.impl.event.WorkflowInstanceEventImpl;
import io.zeebe.gateway.util.ClientRule;
import io.zeebe.gateway.util.Events;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CancelWorkflowInstanceTest {
  public StubBrokerRule brokerRule = new StubBrokerRule();
  public ClientRule clientRule = new ClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException thrown = ExpectedException.none();

  protected ZeebeClient client;

  @Before
  public void setUp() {
    this.client = clientRule.getClient();
  }

  @Test
  public void shouldCancelWorkflowInstance() {
    // given
    final WorkflowInstanceEventImpl baseEvent = Events.exampleWorfklowInstance();
    baseEvent.setPosition(2L);
    baseEvent.setSourceRecordPosition(1L);

    brokerRule.workflowInstances().registerCancelCommand(b -> b.sourceRecordPosition(2L));

    // when
    final WorkflowInstanceEvent workflowInstanceEvent =
        clientRule.workflowClient().newCancelInstanceCommand(baseEvent).send().join();

    // then
    assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

    final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
    assertThat(commandRequest.valueType()).isEqualTo(ValueType.WORKFLOW_INSTANCE);
    assertThat(commandRequest.intent()).isEqualTo(WorkflowInstanceIntent.CANCEL);
    assertThat(commandRequest.key()).isEqualTo(baseEvent.getKey());
    assertThat(commandRequest.sourceRecordPosition()).isEqualTo(2L);
    assertThat(commandRequest.partitionId()).isEqualTo(baseEvent.getMetadata().getPartitionId());
    assertThat(commandRequest.position()).isEqualTo(baseEvent.getMetadata().getPosition());

    assertThat(commandRequest.getCommand())
        .containsOnly(
            entry("bpmnProcessId", baseEvent.getBpmnProcessId()),
            entry("version", (long) baseEvent.getVersion()),
            entry("workflowKey", baseEvent.getWorkflowKey()),
            entry("workflowInstanceKey", baseEvent.getWorkflowInstanceKey()),
            entry("scopeInstanceKey", baseEvent.getScopeInstanceKey()),
            entry("activityId", baseEvent.getActivityId()),
            entry("payload", baseEvent.getPayloadField().getMsgPack()));

    assertThat(workflowInstanceEvent.getState()).isEqualTo(WorkflowInstanceState.CANCELING);
    assertThat(workflowInstanceEvent.getMetadata().getSourceRecordPosition()).isEqualTo(2L);
  }

  @Test
  public void shouldThrowExceptionIfBaseEventIsNull() {
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("base event must not be null");

    // when
    clientRule.workflowClient().newCancelInstanceCommand(null).send().join();
  }

  @Test
  public void shouldThrowExceptionOnRejection() {
    // given
    final WorkflowInstanceEventImpl event = Events.exampleWorfklowInstance();
    event.setKey(2L);

    brokerRule.workflowInstances().registerCancelCommand(b -> b.rejection());

    thrown.expect(ClientCommandRejectedException.class);
    thrown.expectMessage("Command (CANCEL) for event with key 2 was rejected");

    // when
    clientRule.workflowClient().newCancelInstanceCommand(event).send().join();
  }
}
