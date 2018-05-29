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
package io.zeebe.client.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import io.zeebe.client.api.clients.WorkflowClient;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.events.WorkflowInstanceState;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.impl.event.WorkflowInstanceEventImpl;
import io.zeebe.client.util.ClientRule;
import io.zeebe.client.util.Events;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;


public class UpdatePayloadTest
{
    private static final String PAYLOAD = "{ \"foo\" : \"bar\" }";

    private static final byte[] ENCODED_PAYLOAD = new MsgPackConverter().convertToMsgPack(PAYLOAD);

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private WorkflowClient workflowTopicClient;

    @Before
    public void setUp()
    {
        this.workflowTopicClient = clientRule
                .getClient()
                .topicClient()
                .workflowClient();
    }

    @Test
    public void shouldUpdatePayload()
    {
        // given
        final WorkflowInstanceEventImpl baseEvent = Events.exampleWorfklowInstance();
        baseEvent.setKey(2L);
        baseEvent.setWorkflowInstanceKey(1L);

        brokerRule.workflowInstances().registerUpdatedPayloadCommand();

        // when
        final WorkflowInstanceEvent workflowInstanceEvent = workflowTopicClient.newUpdatePayloadCommand(baseEvent)
            .payload(PAYLOAD)
            .send()
            .join();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.valueType()).isEqualTo(ValueType.WORKFLOW_INSTANCE);
        assertThat(request.intent()).isEqualTo(WorkflowInstanceIntent.UPDATE_PAYLOAD);
        assertThat(request.key()).isEqualTo(2L);
        assertThat(request.partitionId()).isEqualTo(baseEvent.getMetadata().getPartitionId());
        assertThat(request.position()).isEqualTo(baseEvent.getMetadata().getPosition());

        assertThat(request.getCommand()).containsOnly(
                entry("bpmnProcessId", baseEvent.getBpmnProcessId()),
                entry("version", baseEvent.getVersion()),
                entry("workflowKey", (int) baseEvent.getWorkflowKey()),
                entry("workflowInstanceKey", (int) baseEvent.getWorkflowInstanceKey()),
                entry("activityId", baseEvent.getActivityId()),
                entry("payload", ENCODED_PAYLOAD));

        assertThat(workflowInstanceEvent.getState()).isEqualByComparingTo(WorkflowInstanceState.PAYLOAD_UPDATED);
    }

    @Test
    public void shouldThrowExceptionOnRejection()
    {
        // given
        final WorkflowInstanceEventImpl event = Events.exampleWorfklowInstance();
        event.setKey(2L);
        event.setWorkflowInstanceKey(1L);

        brokerRule.workflowInstances().registerUpdatedPayloadCommand(r -> r.rejection());

        // then
        thrown.expect(ClientCommandRejectedException.class);
        thrown.expectMessage("Command (UPDATE_PAYLOAD) for event with key 2 was rejected");

        // when
        workflowTopicClient.newUpdatePayloadCommand(event)
            .payload(PAYLOAD)
            .send()
            .join();
    }

    @Test
    public void shouldThrowExceptionIfBaseEventIsNull()
    {
        // then
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("base event must not be null");

        // when
        workflowTopicClient.newUpdatePayloadCommand(null)
            .payload(PAYLOAD)
            .send();
    }

}
