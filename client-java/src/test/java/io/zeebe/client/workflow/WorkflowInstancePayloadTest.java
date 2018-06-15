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

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.util.ClientRule;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.clientapi.*;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.transport.RemoteAddress;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class WorkflowInstancePayloadTest
{

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private final MsgPackConverter converter = new MsgPackConverter();

    private RemoteAddress clientAddress;
    private List<WorkflowInstanceEvent> events;

    @Before
    public void init()
    {
        brokerRule.stubTopicSubscriptionApi(123);

        events = new ArrayList<>();
        clientRule
            .topicClient()
            .newSubscription()
            .name("test")
            .workflowInstanceEventHandler(events::add)
            .open();

        clientAddress = brokerRule.getReceivedCommandRequests().get(0).getSource();
    }

    private WorkflowInstanceEvent workflowInstanceEventWithPayload(byte[] payload)
    {
        brokerRule.newSubscribedEvent()
            .partitionId(StubBrokerRule.TEST_PARTITION_ID)
            .key(4L)
            .position(5L)
            .recordType(RecordType.EVENT)
            .valueType(ValueType.WORKFLOW_INSTANCE)
            .intent(WorkflowInstanceIntent.CREATED)
            .subscriberKey(123L)
            .subscriptionType(SubscriptionType.TOPIC_SUBSCRIPTION)
            .value()
                .put("workflowInstanceKey", 1)
                .put("bpmnProcessId", "workflow")
                .put("version", 1)
                .put("workflowKey", 2)
                .put("payload", payload)
                .done()
            .push(clientAddress);

        waitUntil(() -> events.size() > 0);

        return events.get(0);
    }

    @Test
    public void shouldGetPayloadAsString()
    {
        final WorkflowInstanceEvent event = workflowInstanceEventWithPayload(converter.convertToMsgPack("{\"foo\":\"bar\"}"));

        assertThat(event.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
    }

    @Test
    public void shouldGetPayloadAsMap()
    {
        final WorkflowInstanceEvent event = workflowInstanceEventWithPayload(converter.convertToMsgPack("{\"foo\":\"bar\"}"));

        assertThat(event.getPayloadAsMap()).hasSize(1).containsEntry("foo", "bar");
    }

    @Test
    public void shouldGetPayloadAsType()
    {
        final WorkflowInstanceEvent event = workflowInstanceEventWithPayload(converter.convertToMsgPack("{\"foo\":\"bar\"}"));

        final PayloadObject payload = event.getPayloadAsType(PayloadObject.class);

        assertThat(payload).isNotNull();
        assertThat(payload.foo).isEqualTo("bar");
    }

    @Test
    public void shouldGetNilPayloadAsString()
    {
        final WorkflowInstanceEvent event = workflowInstanceEventWithPayload(MsgPackHelper.NIL);

        assertThat(event.getPayload()).isEqualTo("null");
    }

    @Test
    public void shouldGetNilPayloadAsMap()
    {
        final WorkflowInstanceEvent event = workflowInstanceEventWithPayload(MsgPackHelper.NIL);

        assertThat(event.getPayloadAsMap()).isNull();
    }

    @Test
    public void shouldGetEmptyPayloadAsString()
    {
        final WorkflowInstanceEvent event = workflowInstanceEventWithPayload(MsgPackHelper.EMTPY_OBJECT);

        assertThat(event.getPayload()).isEqualTo("{}");
    }

    @Test
    public void shouldGetEmptyPayloadAsMap()
    {
        final WorkflowInstanceEvent event = workflowInstanceEventWithPayload(MsgPackHelper.EMTPY_OBJECT);

        assertThat(event.getPayloadAsMap()).isEmpty();
    }

    @Test
    public void shouldGetEmptyPayloadAsType()
    {
        final WorkflowInstanceEvent event = workflowInstanceEventWithPayload(MsgPackHelper.EMTPY_OBJECT);

        final PayloadObject payload = event.getPayloadAsType(PayloadObject.class);

        assertThat(payload).isNotNull();
        assertThat(payload.foo).isNull();
    }

    @Test
    public void shouldThrowExceptionIfFailedToDeserializablePayload()
    {
        final WorkflowInstanceEvent event = workflowInstanceEventWithPayload(converter.convertToMsgPack("{\"foo\":123}"));

        exception.expect(ClientException.class);
        exception.expectMessage("Failed deserialize JSON '{\"foo\":123}' to type '" + PayloadObject.class.getName() + "'");

        event.getPayloadAsType(PayloadObject.class);
    }

    public static class PayloadObject
    {
        public String foo;
    }


}
