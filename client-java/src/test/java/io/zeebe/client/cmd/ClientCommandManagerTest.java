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
package io.zeebe.client.cmd;

import static io.zeebe.protocol.clientapi.ControlMessageType.REQUEST_TOPOLOGY;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.CoreMatchers.containsString;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.impl.CommandImpl;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.impl.event.JobEventImpl;
import io.zeebe.client.impl.record.RecordImpl;
import io.zeebe.client.util.ClientRule;
import io.zeebe.client.util.Events;
import io.zeebe.protocol.clientapi.*;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.brokerapi.*;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class ClientCommandManagerTest
{

    public ClientRule clientRule = new ClientRule(b -> b.requestTimeout(Duration.ofSeconds(3)));
    public StubBrokerRule broker = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(broker).around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected ZeebeClient client;

    @Before
    public void setUp()
    {
        client =  clientRule.getClient();
    }

    @Test
    public void testInitialTopologyRequest()
    {
        // when
        waitUntil(() -> broker.getReceivedControlMessageRequests().size() == 1);

        // then
        assertTopologyRefreshRequests(1);
    }

    @Test
    public void testRefreshTopologyRequest()
    {
        // given
        // initial topology has been fetched
        waitUntil(() -> broker.getReceivedControlMessageRequests().size() == 1);

        broker.jobs().registerCreateCommand();

        // extend topology
        broker.addTopic("other-topic", 0);

        // when
        final JobEvent jobEvent = client.topicClient("other-topic").jobClient()
                    .newCreateCommand()
                    .jobType("foo")
                    .send()
                    .join();

        // then the client has refreshed its topology
        assertThat(jobEvent).isNotNull();

        assertTopologyRefreshRequests(2);
    }

    @Test
    public void testRefreshTopologyWhenLeaderIsNotKnown()
    {
        // given
        // initial topology has been fetched
        waitUntil(() -> broker.getReceivedControlMessageRequests().size() == 1);

        broker.jobs().registerCompleteCommand();

        // extend topology
        broker.addTopic("other-topic", 1);

        final JobEventImpl baseEvent = Events.exampleJob();
        baseEvent.setTopicName("other-topic");
        baseEvent.setPartitionId(1);

        // when
        final JobEvent jobEvent = client.topicClient("other-topic").jobClient()
                .newCompleteCommand(baseEvent)
                .send()
                .join();

        // then the client has refreshed its topology
        assertThat(jobEvent).isNotNull();

        assertTopologyRefreshRequests(2);
    }

    @Test
    public void testRequestFailure()
    {
        // given
        broker.onExecuteCommandRequest(ValueType.JOB, JobIntent.CREATE)
                  .respondWithError()
                    .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
                    .errorData("test")
                  .register();

        final ZeebeFuture<JobEvent> future = client.topicClient().jobClient()
                .newCreateCommand()
                .jobType("foo")
                .send();

        assertThatThrownBy(() -> future.join())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Request exception (REQUEST_PROCESSING_FAILURE): test");

        // then
        assertAtLeastTopologyRefreshRequests(1);
        assertCreateJobRequests(1);
    }

    @Test
    public void testReadResponseFailure()
    {
        // given
        broker.jobs().registerCreateCommand();

        final CommandImpl<RecordImpl> command = new CommandImpl<RecordImpl>(((ZeebeClientImpl) client).getCommandManager())
        {
            @Override
            public RecordImpl getCommand()
            {
                return new FailingCommand();
            }
        };

        // then
        exception.expect(ClientException.class);
        exception.expectMessage("Unexpected exception during response handling");

        // when
        command.execute();
    }

    @Test
    public void testPartitionNotFoundResponse()
    {
        // given
        broker.onExecuteCommandRequest(ValueType.JOB, JobIntent.CREATE)
            .respondWithError()
                .errorCode(ErrorCode.PARTITION_NOT_FOUND)
                .errorData("")
            .register();

        // then
        exception.expect(ClientException.class);
        exception.expectMessage(containsString("Request timed out (PT3S)"));
        // when the partition is repeatedly not found, the client loops
        // over refreshing the topology and making a request that fails and so on. The timeout
        // kicks in at any point in that loop, so we cannot assert the exact error message any more specifically.

        // when
        client.topicClient().jobClient()
                .newCreateCommand()
                .jobType("foo")
                .send()
                .join();
    }

    protected void assertTopologyRefreshRequests(final int count)
    {
        final List<ControlMessageRequest> receivedControlMessageRequests = broker.getReceivedControlMessageRequests();
        assertThat(receivedControlMessageRequests).hasSize(count);

        receivedControlMessageRequests.forEach(request ->
        {
            assertThat(request.messageType()).isEqualTo(REQUEST_TOPOLOGY);
            assertThat(request.getData()).isEmpty();
        });
    }

    protected void assertAtLeastTopologyRefreshRequests(final int count)
    {
        final List<ControlMessageRequest> receivedControlMessageRequests = broker.getReceivedControlMessageRequests();
        assertThat(receivedControlMessageRequests.size()).isGreaterThanOrEqualTo(count);

        receivedControlMessageRequests.forEach(request ->
        {
            assertThat(request.messageType()).isEqualTo(REQUEST_TOPOLOGY);
            assertThat(request.getData()).isEmpty();
        });
    }

    protected void assertCreateJobRequests(final int count)
    {
        final List<ExecuteCommandRequest> receivedCommandRequests = broker.getReceivedCommandRequests();
        assertThat(receivedCommandRequests).hasSize(count);

        receivedCommandRequests.forEach(request ->
        {
            assertThat(request.valueType()).isEqualTo(ValueType.JOB);
            assertThat(request.intent()).isEqualTo(JobIntent.CREATE);
        });
    }

    protected static class FailingCommand extends RecordImpl
    {

        @JsonCreator
        public FailingCommand()
        {
            super(null, RecordType.COMMAND, ValueType.JOB);
            this.setTopicName(StubBrokerRule.TEST_TOPIC_NAME);
            this.setPartitionId(StubBrokerRule.TEST_PARTITION_ID);
            this.setIntent(JobIntent.CREATE);
        }

        public String getFailingProp()
        {
            return "foo";
        }

        @Override
        public Class<? extends RecordImpl> getEventClass()
        {
            return FailingEvent.class;
        }
    }

    protected static class FailingEvent extends RecordImpl
    {

        @JsonCreator
        public FailingEvent()
        {
            super(null, RecordType.EVENT, ValueType.JOB);
            this.setTopicName(StubBrokerRule.TEST_TOPIC_NAME);
            this.setPartitionId(StubBrokerRule.TEST_PARTITION_ID);
            this.setIntent(JobIntent.CREATED);
        }

        public void setFailingProp(String prop)
        {
            throw new RuntimeException("expected");
        }

        @Override
        public Class<? extends RecordImpl> getEventClass()
        {
            return FailingEvent.class;
        }
    }

}
