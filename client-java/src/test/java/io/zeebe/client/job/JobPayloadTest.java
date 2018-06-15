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
package io.zeebe.client.job;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.job.subscription.RecordingJobHandler;
import io.zeebe.client.util.ClientRule;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.clientapi.*;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.transport.RemoteAddress;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class JobPayloadTest
{

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private final MsgPackConverter converter = new MsgPackConverter();

    private RemoteAddress clientAddress;
    private RecordingJobHandler jobHandler;

    @Before
    public void init()
    {
        brokerRule.stubJobSubscriptionApi(123L);

        jobHandler = new RecordingJobHandler();
        clientRule.jobClient()
            .newWorker()
            .jobType("test")
            .handler(jobHandler)
            .open();

        clientAddress = brokerRule
                .getReceivedControlMessageRequests()
                .stream()
                .filter((r) -> r.messageType() == ControlMessageType.ADD_JOB_SUBSCRIPTION)
                .findFirst()
                .get()
                .getSource();
    }

    private JobEvent jobEventWithPayload(byte[] payload)
    {
        brokerRule.newSubscribedEvent()
            .partitionId(StubBrokerRule.TEST_PARTITION_ID)
            .key(4L)
            .position(5L)
            .recordType(RecordType.EVENT)
            .valueType(ValueType.JOB)
            .intent(JobIntent.ACTIVATED)
            .subscriberKey(123L)
            .subscriptionType(SubscriptionType.JOB_SUBSCRIPTION)
            .value()
                .put("type", "test")
                .put("deadline", System.currentTimeMillis())
                .put("retries", 3)
                .put("headers", Collections.emptyMap())
                .put("payload", payload)
                .done()
            .push(clientAddress);

        waitUntil(() -> !jobHandler.getHandledJobs().isEmpty());

        return jobHandler.getHandledJobs().get(0);
    }

    @Test
    public void shouldGetPayloadAsString()
    {
        final JobEvent jobEvent = jobEventWithPayload(converter.convertToMsgPack("{\"foo\":\"bar\"}"));

        assertThat(jobEvent.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
    }

    @Test
    public void shouldGetPayloadAsMap()
    {
        final JobEvent jobEvent = jobEventWithPayload(converter.convertToMsgPack("{\"foo\":\"bar\"}"));

        assertThat(jobEvent.getPayloadAsMap()).hasSize(1).containsEntry("foo", "bar");
    }

    @Test
    public void shouldGetPayloadAsObject()
    {
        final JobEvent jobEvent = jobEventWithPayload(converter.convertToMsgPack("{\"foo\":\"bar\"}"));

        final PayloadObject payload = jobEvent.getPayloadAsType(PayloadObject.class);

        assertThat(payload).isNotNull();
        assertThat(payload.foo).isEqualTo("bar");
    }

    @Test
    public void shouldGetNilPayloadAsString()
    {
        final JobEvent jobEvent = jobEventWithPayload(MsgPackHelper.NIL);

        assertThat(jobEvent.getPayload()).isEqualTo("null");
    }

    @Test
    public void shouldGetNilPayloadAsMap()
    {
        final JobEvent jobEvent = jobEventWithPayload(MsgPackHelper.NIL);

        assertThat(jobEvent.getPayloadAsMap()).isNull();
    }

    @Test
    public void shouldGetEmptyPayloadAsString()
    {
        final JobEvent jobEvent = jobEventWithPayload(MsgPackHelper.EMTPY_OBJECT);

        assertThat(jobEvent.getPayload()).isEqualTo("{}");
    }

    @Test
    public void shouldGetEmptyPayloadAsMap()
    {
        final JobEvent jobEvent = jobEventWithPayload(MsgPackHelper.EMTPY_OBJECT);

        assertThat(jobEvent.getPayloadAsMap()).isEmpty();
    }

    @Test
    public void shouldGetEmptyPayloadAsObject()
    {
        final JobEvent jobEvent = jobEventWithPayload(MsgPackHelper.EMTPY_OBJECT);

        final PayloadObject payload = jobEvent.getPayloadAsType(PayloadObject.class);

        assertThat(payload).isNotNull();
        assertThat(payload.foo).isNull();
    }

    @Test
    public void shouldThrowExceptionIfFailedToDeserializablePayload()
    {
        final JobEvent jobEvent = jobEventWithPayload(converter.convertToMsgPack("{\"foo\":123}"));

        exception.expect(ClientException.class);
        exception.expectMessage("Failed deserialize JSON '{\"foo\":123}' to type '" + PayloadObject.class.getName() + "'");

        jobEvent.getPayloadAsType(PayloadObject.class);
    }

    public static class PayloadObject
    {
        public String foo;
    }

}
