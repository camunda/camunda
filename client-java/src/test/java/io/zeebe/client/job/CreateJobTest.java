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
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;

import io.zeebe.client.api.commands.CreateJobCommandStep1;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.JobState;
import io.zeebe.client.api.record.RecordMetadata;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import org.assertj.core.util.Maps;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CreateJobTest
{
    private static final String PAYLOAD = "{\"foo\":\"bar\"}";
    private static final byte[] MSGPACK_PAYLOAD = new MsgPackConverter().convertToMsgPack(PAYLOAD);

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private final Instant now = Instant.now();

    @Before
    public void setUp()
    {
        brokerRule
            .jobs()
            .registerCreateCommand(c -> c
                                   .key(123)
                                   .position(456)
                                   .timestamp(now)
                                   .sourceRecordPosition(1L)
                                   .value()
                                       .allOf(r -> r.getCommand())
                                       .put("worker", "")
                                       .done());
    }

    @Test
    public void shouldCreateJob()
    {
        // given

        // when
        final JobEvent job = clientRule.jobClient()
            .newCreateCommand()
            .jobType("fooType")
            .retries(3)
            .addCustomHeader("beverage", "apple juice")
            .payload(PAYLOAD)
            .send()
            .join();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.valueType()).isEqualTo(ValueType.JOB);
        assertThat(request.intent()).isEqualTo(JobIntent.CREATE);
        assertThat(request.partitionId()).isEqualTo(StubBrokerRule.TEST_PARTITION_ID);
        assertThat(request.position()).isEqualTo(ExecuteCommandRequestEncoder.positionNullValue());

        assertThat(request.getCommand()).containsOnly(
                entry("retries", 3),
                entry("type", "fooType"),
                entry("headers", new HashMap<>()),
                entry("customHeaders", Maps.newHashMap("beverage", "apple juice")),
                entry("payload", MSGPACK_PAYLOAD));

        assertThat(job.getKey()).isEqualTo(123L);

        final RecordMetadata metadata = job.getMetadata();
        assertThat(metadata.getTopicName()).isEqualTo(StubBrokerRule.TEST_TOPIC_NAME);
        assertThat(metadata.getPartitionId()).isEqualTo(StubBrokerRule.TEST_PARTITION_ID);
        assertThat(metadata.getPosition()).isEqualTo(456);
        assertThat(metadata.getTimestamp()).isEqualTo(now);
        assertThat(metadata.getRejectionType()).isEqualTo(null);
        assertThat(metadata.getRejectionReason()).isEqualTo(null);
        assertThat(metadata.getSourceRecordPosition()).isEqualTo(1L);

        assertThat(job.getState()).isEqualTo(JobState.CREATED);
        assertThat(job.getHeaders()).isEmpty();
        assertThat(job.getCustomHeaders()).containsOnly(entry("beverage", "apple juice"));
        assertThat(job.getDeadline()).isNull();
        assertThat(job.getWorker()).isEmpty();
        assertThat(job.getRetries()).isEqualTo(3);
        assertThat(job.getType()).isEqualTo("fooType");
        assertThat(job.getPayload()).isEqualTo(PAYLOAD);
    }

    @Test
    public void shouldCreateJobWithDefaultValues()
    {
        // when
        clientRule.jobClient()
            .newCreateCommand()
            .jobType("fooType")
            .send()
            .join();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.getCommand()).containsOnly(
                entry("retries", CreateJobCommandStep1.DEFAULT_RETRIES),
                entry("type", "fooType"),
                entry("headers", Collections.emptyMap()),
                entry("customHeaders", Collections.emptyMap()));
    }

    @Test
    public void shouldSetPayloadAsStream()
    {
        // when
        clientRule.jobClient()
            .newCreateCommand()
            .jobType("fooType")
            .payload(new ByteArrayInputStream(PAYLOAD.getBytes(StandardCharsets.UTF_8)))
            .send().join();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.getCommand()).contains(entry("payload", MSGPACK_PAYLOAD));
    }

    @Test
    public void shouldSetPayloadAsMap()
    {
        // when
        clientRule
            .jobClient()
            .newCreateCommand()
            .jobType("foo")
            .payload(Collections.singletonMap("foo", "bar"))
            .send()
            .join();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.getCommand()).contains(entry("payload", MSGPACK_PAYLOAD));
    }

    @Test
    public void shouldSetPayloadAsObject()
    {
        final PayloadObject payload = new PayloadObject();
        payload.foo = "bar";

        // when
        clientRule
            .jobClient()
            .newCreateCommand()
            .jobType("foo")
            .payload(payload)
            .send()
            .join();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.getCommand()).contains(entry("payload", MSGPACK_PAYLOAD));
    }

    @Test
    public void shouldValidateTypeNotNull()
    {
        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("type must not be null");

        // when
        clientRule.jobClient()
            .newCreateCommand()
            .jobType(null)
            .send();
    }

    @Test
    public void shouldThrowExceptionIfFailedToSerializePayload()
    {
        class NotSerializable
        { }

        // then
        exception.expect(ClientException.class);
        exception.expectMessage("Failed to serialize object");

        // when
        clientRule
            .jobClient()
            .newCreateCommand()
            .jobType("foo")
            .payload(new NotSerializable())
            .send()
            .join();
    }

    public static class PayloadObject
    {
        public String foo;
    }
}
