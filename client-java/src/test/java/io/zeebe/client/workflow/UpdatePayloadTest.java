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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import io.zeebe.client.WorkflowsClient;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.util.ClientRule;
import io.zeebe.client.util.Events;
import io.zeebe.client.workflow.impl.WorkflowInstanceEventImpl;
import io.zeebe.protocol.clientapi.EventType;
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

    private WorkflowsClient workflowTopicClient;

    @Before
    public void setUp()
    {
        this.workflowTopicClient = clientRule
                .getClient()
                .workflows();
    }

    @Test
    public void shouldUpdatePayload()
    {
        // given
        final WorkflowInstanceEventImpl event = Events.exampleWorfklowInstance();
        event.setKey(2L);
        event.setWorkflowInstanceKey(1L);

        brokerRule.onExecuteCommandRequest().respondWith()
            .key(r -> r.key())
            .value()
                .put("state", "PAYLOAD_UPDATED")
                .done()
            .register();

        // when
        workflowTopicClient.updatePayload(event)
            .payload(PAYLOAD)
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.eventType()).isEqualTo(EventType.WORKFLOW_INSTANCE_EVENT);
        assertThat(request.key()).isEqualTo(2L);
        assertThat(request.getCommand())
            .containsEntry("state", "UPDATE_PAYLOAD")
            .containsEntry("workflowInstanceKey", 1)
            .containsEntry("payload", ENCODED_PAYLOAD);
    }

    @Test
    public void shouldRejectUpdatePayload()
    {
        // given
        final WorkflowInstanceEventImpl event = Events.exampleWorfklowInstance();
        event.setKey(2L);
        event.setWorkflowInstanceKey(1L);

        brokerRule.onExecuteCommandRequest().respondWith()
            .key(r -> r.key())
            .value()
                .allOf(r -> r.getCommand())
                .put("state", "UPDATE_PAYLOAD_REJECTED")
                .done()
            .register();

        // then
        thrown.expect(ClientCommandRejectedException.class);
        thrown.expectMessage("Command for event with key 2 was rejected by broker (UPDATE_PAYLOAD_REJECTED)");

        // when
        workflowTopicClient.updatePayload(event)
            .payload(PAYLOAD)
            .execute();
    }

    @Test
    public void shouldFailIfWorkflowEventIsNull()
    {
        // then
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("base event must not be null");

        // when
        workflowTopicClient.updatePayload(null)
            .payload(PAYLOAD)
            .execute();
    }


}
