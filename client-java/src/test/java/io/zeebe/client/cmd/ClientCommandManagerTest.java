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
import static io.zeebe.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_PARTITION_ID;
import static io.zeebe.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_TOPIC_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.internal.matchers.ThrowableMessageMatcher.hasMessage;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.util.HashMap;
import java.util.List;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.impl.ClientCommandController;
import io.zeebe.client.task.cmd.CreateTaskCmd;
import io.zeebe.client.task.impl.CreateTaskCmdImpl;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.brokerapi.ControlMessageRequest;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import org.agrona.MutableDirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class ClientCommandManagerTest
{

    public ClientRule clientRule = new ClientRule();
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
        stubTaskResponse();
        createTaskCmd().execute();

        // then
        assertTopologyRefreshRequests(1);
        assertCreateTaskRequests(1);
    }

    @Test
    public void testRefreshTopologyRequest()
    {
        // given
        stubTaskResponse();
        final CreateTaskCmd command = createTaskCmd("other-topic", 0);

        exception.expect(RuntimeException.class);
        exception.expectMessage("Cannot execute command. No broker for topic with name 'other-topic' and partition id '0' found.");

        // when
        command.execute();

        // then
        // +1 for initial topology lookup
        assertTopologyRefreshRequests(ClientCommandController.DEFAULT_RETRIES + 1);
        assertCreateTaskRequests(0);
    }

    @Test
    public void testRequestFailure()
    {
        // given
        stubRequestProcessingFailureResponse();
        final CreateTaskCmd command = createTaskCmd();

        exception.expect(RuntimeException.class);
        exception.expectMessage("Request exception (REQUEST_PROCESSING_FAILURE): test");

        // when
        command.execute();

        // then
        assertTopologyRefreshRequests(1);
        assertCreateTaskRequests(1);
    }

    @Test
    public void testReadResponseFailure()
    {
        // given
        stubTaskResponse();
        final CreateTaskCmd command = createFailingTaskCmd();

        exception.expect(RuntimeException.class);
        exception.expectMessage("Cannot execute command. No broker for topic with name 'default-topic' and partition id '0' found.");
        exception.expectCause(allOf(instanceOf(RuntimeException.class), hasMessage(equalTo("test"))));

        // when
        command.execute();

        // then
        assertTopologyRefreshRequests(1);
        assertCreateTaskRequests(1);
    }

    @Test
    public void testTopicNotFoundResponse()
    {
        // given
        stubTopicNotFoundResponse();

        exception.expect(RuntimeException.class);
        exception.expectMessage("Cannot execute command. No broker for topic with name 'default-topic' and partition id '0' found.");

        // when
        createTaskCmd().execute();

        // then
        // +1 for initial topology lookup
        assertTopologyRefreshRequests(ClientCommandController.DEFAULT_RETRIES + 1);
        assertCreateTaskRequests(ClientCommandController.DEFAULT_RETRIES);
    }


    protected CreateTaskCmd createTaskCmd()
    {
        return createTaskCmd(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);
    }

    protected CreateTaskCmd createTaskCmd(final String topicName, final int partitionId)
    {
        return client.taskTopic(topicName, partitionId).create().taskType("test");
    }

    protected CreateTaskCmd createFailingTaskCmd()
    {
        final CreateTaskCmd spy = spy(createTaskCmd());
        doThrow(new RuntimeException("test")).when(((CreateTaskCmdImpl) spy)).readResponse(any(MutableDirectBuffer.class), anyInt(), anyInt(), anyInt());
        return spy;
    }

    protected void stubTaskResponse()
    {
        broker.onExecuteCommandRequest(ecr -> ecr.eventType() == EventType.TASK_EVENT &&
            "CREATE".equals(ecr.getCommand().get("eventType")))
              .respondWith()
                  .topicName(DEFAULT_TOPIC_NAME)
                  .partitionId(DEFAULT_PARTITION_ID)
                  .key(123)
                  .event()
                  .allOf(ExecuteCommandRequest::getCommand)
                      .put("eventType", "CREATED")
                      .put("headers", new HashMap<>())
                      .put("payload", new byte[0])
                  .done()
              .register();
    }

    protected void stubRequestProcessingFailureResponse()
    {
        broker.onExecuteCommandRequest(ecr -> ecr.eventType() == EventType.TASK_EVENT &&
            "CREATE".equals(ecr.getCommand().get("eventType")))
              .respondWithError()
                .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
                .errorData("test")
              .register();
    }

    protected void stubTopicNotFoundResponse()
    {
        broker.onExecuteCommandRequest(ecr -> ecr.eventType() == EventType.TASK_EVENT &&
            "CREATE".equals(ecr.getCommand().get("eventType")))
              .respondWithError()
                  .errorCode(ErrorCode.TOPIC_NOT_FOUND)
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
            assertThat(request.getData()).isNull();
        });
    }

    protected void assertCreateTaskRequests(final int count)
    {
        final List<ExecuteCommandRequest> receivedCommandRequests = broker.getReceivedCommandRequests();
        assertThat(receivedCommandRequests).hasSize(count);

        receivedCommandRequests.forEach(request ->
        {
            assertThat(request.eventType()).isEqualTo(EventType.TASK_EVENT);
            assertThat(request.getCommand().get("eventType")).isEqualTo("CREATE");
        });
    }

}
