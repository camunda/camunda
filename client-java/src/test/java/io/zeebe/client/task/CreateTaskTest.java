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
package io.zeebe.client.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import org.assertj.core.util.Maps;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.task.cmd.CreateTaskCommand;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;

public class CreateTaskTest
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
    public void shouldCreateTask()
    {
        // given
        brokerRule.onExecuteCommandRequest(EventType.TASK_EVENT, "CREATE")
            .respondWith()
            .key(123)
            .position(456)
            .value()
              .allOf((r) -> r.getCommand())
              .put("state", "CREATED")
              .put("lockTime", Protocol.INSTANT_NULL_VALUE)
              .put("lockOwner", "")
              .done()
            .register();

        final String payload = "{\"foo\":\"bar\"}";

        // when
        final TaskEvent taskEvent = clientRule.tasks()
            .create(clientRule.getDefaultTopicName(), "fooType")
            .retries(3)
            .addCustomHeader("beverage", "apple juice")
            .payload(payload)
            .execute();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.eventType()).isEqualTo(EventType.TASK_EVENT);
        assertThat(request.partitionId()).isEqualTo(StubBrokerRule.TEST_PARTITION_ID);
        assertThat(request.position()).isEqualTo(ExecuteCommandRequestEncoder.positionNullValue());

        assertThat(request.getCommand()).containsOnly(
                entry("state", "CREATE"),
                entry("retries", 3),
                entry("type", "fooType"),
                entry("headers", new HashMap<>()),
                entry("customHeaders", Maps.newHashMap("beverage", "apple juice")),
                entry("lockTime", Protocol.INSTANT_NULL_VALUE),
                entry("payload", converter.convertToMsgPack(payload)));

        assertThat(taskEvent.getMetadata().getKey()).isEqualTo(123L);
        assertThat(taskEvent.getMetadata().getTopicName()).isEqualTo(StubBrokerRule.TEST_TOPIC_NAME);
        assertThat(taskEvent.getMetadata().getPartitionId()).isEqualTo(StubBrokerRule.TEST_PARTITION_ID);
        assertThat(taskEvent.getMetadata().getPosition()).isEqualTo(456);

        assertThat(taskEvent.getState()).isEqualTo("CREATED");
        assertThat(taskEvent.getHeaders()).isEmpty();
        assertThat(taskEvent.getCustomHeaders()).containsOnly(entry("beverage", "apple juice"));
        assertThat(taskEvent.getLockExpirationTime()).isNull();
        assertThat(taskEvent.getLockOwner()).isEmpty();
        assertThat(taskEvent.getRetries()).isEqualTo(3);
        assertThat(taskEvent.getType()).isEqualTo("fooType");
        assertThat(taskEvent.getPayload()).isEqualTo(payload);
    }

    @Test
    public void shouldCreateTaskWithDefaultValues()
    {
        // given
        brokerRule.onExecuteCommandRequest(EventType.TASK_EVENT, "CREATE")
            .respondWith()
            .key(123)
            .value()
              .allOf((r) -> r.getCommand())
              .put("state", "CREATED")
              .put("headers", new HashMap<>())
              .put("payload", MsgPackUtil.encodeMsgPack(w -> w.packNil()).byteArray())
              .done()
            .register();

        // when
        final TaskEvent taskEvent = clientRule.tasks()
            .create(clientRule.getDefaultTopicName(), "fooType")
            .execute();

        // then
        assertThat(taskEvent.getMetadata().getKey()).isEqualTo(123L);
        assertThat(taskEvent.getMetadata().getTopicName()).isEqualTo(StubBrokerRule.TEST_TOPIC_NAME);
        assertThat(taskEvent.getMetadata().getPartitionId()).isEqualTo(StubBrokerRule.TEST_PARTITION_ID);

        assertThat(taskEvent.getRetries()).isEqualTo(CreateTaskCommand.DEFAULT_RETRIES);
        assertThat(taskEvent.getHeaders()).isEmpty();
        assertThat(taskEvent.getPayload()).isEqualTo("null");
    }

    @Test
    public void shouldSetPayloadAsStream()
    {
        // given
        brokerRule.onExecuteCommandRequest(EventType.TASK_EVENT, "CREATE")
            .respondWith()
            .key(123)
            .value()
              .allOf((r) -> r.getCommand())
              .put("state", "CREATED")
              .put("lockTime", Protocol.INSTANT_NULL_VALUE)
              .put("lockOwner", "")
              .done()
            .register();

        final String payload = "{\"foo\":\"bar\"}";

        // when
        clientRule.tasks()
            .create(clientRule.getDefaultTopicName(), "fooType")
            .payload(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)))
            .execute();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.getCommand()).contains(
                entry("payload", converter.convertToMsgPack(payload)));
    }

    @Test
    public void shouldValidateTopicNameNotNull()
    {
        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("topic must not be null");

        // when
        clientRule.tasks()
            .create(null, "fooType")
            .execute();
    }

    @Test
    public void shouldValidateTypeNotNull()
    {
        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("type must not be null");

        // when
        clientRule.tasks()
            .create("topic", null)
            .execute();
    }

    @Test
    public void testValidateTopicNameNotEmpty()
    {
        // given
        final ZeebeClient client = clientRule.getClient();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("topic must not be empty");

        // when
        client.tasks().create("", "foo").execute();
    }

    @Test
    public void testValidateTopicNameNotNull()
    {
        // given
        final ZeebeClient client = clientRule.getClient();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("topic must not be null");

        // when
        client.tasks().create(null, "foo").execute();
    }

}
