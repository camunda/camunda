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
import java.util.HashMap;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.CreateJobCommandStep1;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.JobEvent.JobState;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.assertj.core.util.Maps;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CreateJobTest
{

    public ActorSchedulerRule schedulerRule = new ActorSchedulerRule();
    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected ZeebeClient client;

    protected final MsgPackConverter converter = new MsgPackConverter();


    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
    }

    @Test
    public void shouldCreateJob()
    {
        // given
        brokerRule.onExecuteCommandRequest(ValueType.JOB, JobIntent.CREATE)
            .respondWith()
            .event()
            .key(123)
            .position(456)
            .intent(JobIntent.CREATED)
            .value()
              .allOf((r) -> r.getCommand())
              .put("lockTime", Protocol.INSTANT_NULL_VALUE)
              .put("lockOwner", "")
              .done()
            .register();

        final String payload = "{\"foo\":\"bar\"}";

        // when
        final JobEvent job = clientRule.jobClient()
            .newCreateCommand()
            .jobType("fooType")
            .retries(3)
            .addCustomHeader("beverage", "apple juice")
            .payload(payload)
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
                entry("lockTime", Protocol.INSTANT_NULL_VALUE),
                entry("payload", converter.convertToMsgPack(payload)));

        assertThat(job.getKey()).isEqualTo(123L);
        assertThat(job.getMetadata().getTopicName()).isEqualTo(StubBrokerRule.TEST_TOPIC_NAME);
        assertThat(job.getMetadata().getPartitionId()).isEqualTo(StubBrokerRule.TEST_PARTITION_ID);
        assertThat(job.getMetadata().getPosition()).isEqualTo(456);

        assertThat(job.getState()).isEqualTo(JobState.CREATED);
        assertThat(job.getHeaders()).isEmpty();
        assertThat(job.getCustomHeaders()).containsOnly(entry("beverage", "apple juice"));
        assertThat(job.getLockExpirationTime()).isNull();
        assertThat(job.getLockOwner()).isEmpty();
        assertThat(job.getRetries()).isEqualTo(3);
        assertThat(job.getType()).isEqualTo("fooType");
        assertThat(job.getPayload()).isEqualTo(payload);
    }

    @Test
    public void shouldCreateJobWithDefaultValues()
    {
        // given
        brokerRule.onExecuteCommandRequest(ValueType.JOB, JobIntent.CREATE)
            .respondWith()
            .event()
            .key(123)
            .intent(JobIntent.CREATED)
            .value()
              .allOf((r) -> r.getCommand())
              .put("headers", new HashMap<>())
              .put("payload", MsgPackUtil.encodeMsgPack(w -> w.packNil()).byteArray())
              .done()
            .register();

        // when
        final JobEvent job = clientRule.jobClient()
            .newCreateCommand()
            .jobType("fooType")
            .send()
            .join();

        // then
        assertThat(job.getMetadata().getKey()).isEqualTo(123L);
        assertThat(job.getMetadata().getTopicName()).isEqualTo(StubBrokerRule.TEST_TOPIC_NAME);
        assertThat(job.getMetadata().getPartitionId()).isEqualTo(StubBrokerRule.TEST_PARTITION_ID);

        assertThat(job.getRetries()).isEqualTo(CreateJobCommandStep1.DEFAULT_RETRIES);
        assertThat(job.getHeaders()).isEmpty();
        assertThat(job.getPayload()).isEqualTo("null");
    }

    @Test
    public void shouldSetPayloadAsStream()
    {
        // given
        brokerRule.onExecuteCommandRequest(ValueType.JOB, JobIntent.CREATE)
            .respondWith()
            .event()
            .key(123)
            .intent(JobIntent.CREATED)
            .value()
              .allOf((r) -> r.getCommand())
              .put("lockTime", Protocol.INSTANT_NULL_VALUE)
              .put("lockOwner", "")
              .done()
            .register();

        final String payload = "{\"foo\":\"bar\"}";

        // when
        clientRule.jobClient()
            .newCreateCommand()
            .jobType("fooType")
            .payload(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)))
            .send().join();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.getCommand()).contains(
                entry("payload", converter.convertToMsgPack(payload)));
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

}
