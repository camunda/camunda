/**
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
import static io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule.TEST_PARTITION_ID;
import static io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule.TEST_TOPIC_NAME;

import io.zeebe.client.WorkflowTopicClient;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;


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

    private WorkflowTopicClient workflowTopicClient;

    @Before
    public void setUp()
    {
        this.workflowTopicClient = clientRule
                .getClient()
                .workflowTopic(TEST_TOPIC_NAME, TEST_PARTITION_ID);
    }

    @Test
    public void shouldUpdatePayload()
    {
        // given
        brokerRule.onExecuteCommandRequest().respondWith()
            .topicName(TEST_TOPIC_NAME)
            .partitionId(TEST_PARTITION_ID)
            .key(r -> r.key())
            .event()
                .put("eventType", "PAYLOAD_UPDATED")
                .done()
            .register();

        // when
        workflowTopicClient.updatePayload()
            .workflowInstanceKey(1L)
            .activityInstanceKey(2L)
            .payload(PAYLOAD)
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.eventType()).isEqualTo(EventType.WORKFLOW_EVENT);
        assertThat(request.key()).isEqualTo(2L);
        assertThat(request.getCommand())
            .containsEntry("eventType", "UPDATE_PAYLOAD")
            .containsEntry("workflowInstanceKey", 1)
            .containsEntry("payload", ENCODED_PAYLOAD);
    }

    @Test
    public void shouldRejectUpdatePayload()
    {
        // given
        brokerRule.onExecuteCommandRequest().respondWith()
            .topicName(TEST_TOPIC_NAME)
            .partitionId(TEST_PARTITION_ID)
            .key(r -> r.key())
            .event()
                .allOf(r -> r.getCommand())
                .put("eventType", "UPDATE_PAYLOAD_REJECTED")
                .done()
            .register();

        // then
        thrown.expect(ClientCommandRejectedException.class);
        thrown.expectMessage("Failed to update payload of the workflow instance with key '1'");

        // when
        workflowTopicClient.updatePayload()
            .workflowInstanceKey(1L)
            .activityInstanceKey(2L)
            .payload(PAYLOAD)
            .execute();
    }

    @Test
    public void shouldFailIfActivityInstanceKeyMissing()
    {
        // then
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("activity instance key must be greater than 0");

        // when
        workflowTopicClient.updatePayload()
            .workflowInstanceKey(1L)
            .payload(PAYLOAD)
            .execute();
    }

    @Test
    public void shouldFailIfWorkflowInstanceKeyMissing()
    {
        // then
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("workflow instance key must be greater than 0");

        // when
        workflowTopicClient.updatePayload()
            .activityInstanceKey(2L)
            .payload(PAYLOAD)
            .execute();
    }

    @Test
    public void shouldFailIfPayloadMissing()
    {
        // then
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("payload must not be null");

        // when
        workflowTopicClient.updatePayload()
            .workflowInstanceKey(1L)
            .activityInstanceKey(2L)
            .execute();
    }

}
