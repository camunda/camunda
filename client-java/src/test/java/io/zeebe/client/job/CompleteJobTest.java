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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.JobState;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.impl.event.JobEventImpl;
import io.zeebe.client.util.ClientRule;
import io.zeebe.client.util.Events;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CompleteJobTest
{
    private static final String PAYLOAD = "{\"fruit\":\"cherry\"}";
    private static final byte[] MSGPACK_PAYLOAD = new MsgPackConverter().convertToMsgPack(PAYLOAD);

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void init()
    {
        brokerRule.jobs().registerCompleteCommand();
    }

    @Test
    public void shouldCompleteJob()
    {
        // given
        final JobEventImpl baseEvent = Events.exampleJob();
        baseEvent.setPosition(2L);
        baseEvent.setSourceRecordPosition(1L);

        brokerRule.jobs()
            .registerCompleteCommand(
                executeCommandResponseBuilder -> executeCommandResponseBuilder.sourceRecordPosition(3L));

        // when
        final JobEvent jobEvent = clientRule.jobClient()
            .newCompleteCommand(baseEvent)
            .payload(PAYLOAD)
            .send()
            .join();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.valueType()).isEqualTo(ValueType.JOB);
        assertThat(request.partitionId()).isEqualTo(StubBrokerRule.TEST_PARTITION_ID);
        assertThat(request.position()).isEqualTo(baseEvent.getMetadata().getPosition());
        assertThat(request.sourceRecordPosition()).isEqualTo(1L);

        assertThat(request.getCommand()).containsOnly(
                entry("deadline", baseEvent.getDeadline().toEpochMilli()),
                entry("worker", baseEvent.getWorker()),
                entry("retries", baseEvent.getRetries()),
                entry("type", baseEvent.getType()),
                entry("headers", baseEvent.getHeaders()),
                entry("customHeaders", baseEvent.getCustomHeaders()),
                entry("payload", MSGPACK_PAYLOAD));

        assertThat(jobEvent.getMetadata().getKey()).isEqualTo(baseEvent.getKey());
        assertThat(jobEvent.getMetadata().getTopicName()).isEqualTo(StubBrokerRule.TEST_TOPIC_NAME);
        assertThat(jobEvent.getMetadata().getPartitionId()).isEqualTo(StubBrokerRule.TEST_PARTITION_ID);
        assertThat(jobEvent.getMetadata().getSourceRecordPosition()).isEqualTo(3L);
        assertThat(jobEvent.getSourceRecordPosition()).isEqualTo(3L);

        assertThat(jobEvent.getState()).isEqualTo(JobState.COMPLETED);
        assertThat(jobEvent.getHeaders()).isEqualTo(baseEvent.getHeaders());
        assertThat(jobEvent.getDeadline()).isEqualTo(baseEvent.getDeadline());
        assertThat(jobEvent.getWorker()).isEqualTo(baseEvent.getWorker());
        assertThat(jobEvent.getRetries()).isEqualTo(baseEvent.getRetries());
        assertThat(jobEvent.getType()).isEqualTo(baseEvent.getType());
        assertThat(jobEvent.getPayload()).isEqualTo(PAYLOAD);
    }

    @Test
    public void shouldCompleteWithoutPayload()
    {
        // given
        final JobEventImpl baseEvent = Events.exampleJob();

        // when
        clientRule.jobClient()
            .newCompleteCommand(baseEvent)
            .withoutPayload()
            .send()
            .join();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);

        assertThat(request.getCommand()).doesNotContainKey("payload");
    }

    @Test
    public void shouldThrowExceptionOnRejection()
    {
        // given
        final JobEventImpl baseEvent = Events.exampleJob();

        brokerRule.jobs().registerCompleteCommand(b -> b.rejection());

        // then
        exception.expect(ClientCommandRejectedException.class);
        exception.expectMessage("Command (COMPLETE) for event with key 79 was rejected");

        // when
        clientRule.jobClient()
            .newCompleteCommand(baseEvent)
            .payload(PAYLOAD)
            .send()
            .join();
    }

    @Test
    public void shouldThrowExceptionIfBaseEventIsNull()
    {
        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("base event must not be null");

        // when
        clientRule.jobClient()
            .newCompleteCommand(null)
            .payload(PAYLOAD)
            .send()
            .join();
    }

    @Test
    public void shouldSetPayloadAsStream()
    {
        // given
        final JobEventImpl baseEvent = Events.exampleJob();

        final ByteArrayInputStream inStream =
                new ByteArrayInputStream(PAYLOAD.getBytes(StandardCharsets.UTF_8));

        // when
        clientRule.jobClient()
            .newCompleteCommand(baseEvent)
            .payload(inStream)
            .send()
            .join();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);

        assertThat(request.getCommand()).contains(
                entry("payload", MSGPACK_PAYLOAD));
    }

    @Test
    public void shouldSetPayloadAsMap()
    {
        // given
        final JobEventImpl baseEvent = Events.exampleJob();

        // when
        clientRule.jobClient()
            .newCompleteCommand(baseEvent)
            .payload(Collections.singletonMap("fruit", "cherry"))
            .send()
            .join();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.getCommand()).contains(entry("payload", MSGPACK_PAYLOAD));
    }

    @Test
    public void shouldSetPayloadAsObject()
    {
        // given
        final JobEventImpl baseEvent = Events.exampleJob();
        final PayloadObject payload = new PayloadObject();
        payload.fruit = "cherry";

        // when
        clientRule.jobClient()
            .newCompleteCommand(baseEvent)
            .payload(payload)
            .send()
            .join();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.getCommand()).contains(entry("payload", MSGPACK_PAYLOAD));
    }

    @Test
    public void shouldThrowExceptionIfFailedToSerializePayload()
    {
        // given
        final JobEventImpl baseEvent = Events.exampleJob();

        class NotSerializable
        { }

        // then
        exception.expect(ClientException.class);
        exception.expectMessage("Failed to serialize object");

        // when
        clientRule.jobClient()
            .newCompleteCommand(baseEvent)
            .payload(new NotSerializable())
            .send()
            .join();
    }

    public static class PayloadObject
    {
        public String fruit;
    }

}
