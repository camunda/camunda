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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.TopicEventType;
import io.zeebe.client.event.impl.EventImpl;
import io.zeebe.client.event.impl.TaskEventImpl;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.impl.cmd.CommandImpl;
import io.zeebe.client.task.cmd.CreateTaskCommand;
import io.zeebe.client.util.ClientRule;
import io.zeebe.client.util.Events;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.brokerapi.ControlMessageRequest;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;

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

        stubCreateTaskResponse();

        // extend topology
        broker.addTopic("other-topic", 0);

        // when
        final TaskEvent taskEvent = createTaskCmd("other-topic").execute();

        // then the client has refreshed its topology
        assertThat(taskEvent).isNotNull();

        assertTopologyRefreshRequests(2);
    }

    @Test
    public void testRefreshTopologyWhenLeaderIsNotKnown()
    {
        // given
        // initial topology has been fetched
        waitUntil(() -> broker.getReceivedControlMessageRequests().size() == 1);

        stubCompleteTaskResponse();

        // extend topology
        broker.addTopic("other-topic", 1);

        final TaskEventImpl task = Events.exampleTask();
        task.setTopicName("other-topic");
        task.setPartitionId(1);

        // when
        final TaskEvent result = client.tasks().complete(task).execute();

        // then the client has refreshed its topology
        assertThat(result).isNotNull();

        assertTopologyRefreshRequests(2);
    }

    @Test
    public void testRequestFailure()
    {
        // given
        stubRequestProcessingFailureResponse();
        final CreateTaskCommand command = createTaskCmd();

        // when
        assertThatThrownBy(command::execute)
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Request exception (REQUEST_PROCESSING_FAILURE): test");

        // then
        assertAtLeastTopologyRefreshRequests(1);
        assertCreateTaskRequests(1);
    }

    @Test
    public void testReadResponseFailure()
    {
        // given
        stubCreateTaskResponse();

        final FailingCommand command = new FailingCommand(((ZeebeClientImpl) client).getCommandManager());

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
        stubPartitionNotFoundResponse();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage(containsString("Request timed out (PT3S)"));
        // when the partition is repeatedly not found, the client loops
        // over refreshing the topology and making a request that fails and so on. The timeout
        // kicks in at any point in that loop, so we cannot assert the exact error message any more specifically.

        // when
        createTaskCmd().execute();
    }


    protected CreateTaskCommand createTaskCmd()
    {
        return createTaskCmd(clientRule.getDefaultTopicName());
    }

    protected CreateTaskCommand createTaskCmd(final String topicName)
    {
        return client.tasks().create(topicName, "test");
    }

    protected void stubCreateTaskResponse()
    {
        broker.onExecuteCommandRequest(EventType.TASK_EVENT, "CREATE")
            .respondWith()
            .partitionId(StubBrokerRule.TEST_PARTITION_ID)
            .key(123)
            .value()
              .allOf((r) -> r.getCommand())
              .put("state", "CREATED")
              .put("lockTime", Protocol.INSTANT_NULL_VALUE)
              .put("lockOwner", "")
              .done()
            .register();
    }

    protected void stubCompleteTaskResponse()
    {
        broker.onExecuteCommandRequest(EventType.TASK_EVENT, "COMPLETE")
            .respondWith()
            .partitionId(r -> r.partitionId())
            .key(r -> r.key())
            .value()
              .allOf((r) -> r.getCommand())
              .put("state", "COMPLETED")
              .done()
            .register();
    }

    protected void stubRequestProcessingFailureResponse()
    {
        broker.onExecuteCommandRequest(ecr -> ecr.eventType() == EventType.TASK_EVENT &&
            "CREATE".equals(ecr.getCommand().get("state")))
              .respondWithError()
                .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
                .errorData("test")
              .register();
    }

    protected void stubPartitionNotFoundResponse()
    {
        broker.onExecuteCommandRequest(EventType.TASK_EVENT, "CREATE")
              .respondWithError()
                  .errorCode(ErrorCode.PARTITION_NOT_FOUND)
                  .errorData("")
              .register();
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

    protected void assertCreateTaskRequests(final int count)
    {
        final List<ExecuteCommandRequest> receivedCommandRequests = broker.getReceivedCommandRequests();
        assertThat(receivedCommandRequests).hasSize(count);

        receivedCommandRequests.forEach(request ->
        {
            assertThat(request.eventType()).isEqualTo(EventType.TASK_EVENT);
            assertThat(request.getCommand().get("state")).isEqualTo("CREATE");
        });
    }

    protected static class FailingCommand extends CommandImpl<EventImpl>
    {
        public FailingCommand(RequestManager client)
        {
            super(client);
        }

        @Override
        public EventImpl getEvent()
        {
            return new FailingEvent("CREATE");
        }

        @Override
        public String getExpectedStatus()
        {
            return "CREATED";
        }
    }

    protected static class FailingEvent extends EventImpl
    {

        @JsonCreator
        public FailingEvent(@JsonProperty("state") String state)
        {
            super(TopicEventType.TASK, state);
            this.setTopicName(StubBrokerRule.TEST_TOPIC_NAME);
            this.setPartitionId(StubBrokerRule.TEST_PARTITION_ID);
        }

        public String getFailingProp()
        {
            return "foo";
        }

        public void setFailingProp(String prop)
        {
            throw new RuntimeException("expected");
        }

    }

}
