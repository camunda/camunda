/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.client.cmd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.CloseTaskSubscriptionCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskSubscription;
import org.camunda.tngp.protocol.clientapi.ControlMessageRequestDecoder;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CloseTaskSubscriptionCmdTest
{
    private static final byte[] BUFFER = new byte[1014 * 1024];

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final ControlMessageRequestDecoder requestDecoder = new ControlMessageRequestDecoder();

    private final UnsafeBuffer writeBuffer = new UnsafeBuffer(0, 0);

    private CloseTaskSubscriptionCmdImpl command;
    private ObjectMapper objectMapper;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup()
    {
        final ClientCmdExecutor clientCmdExecutor = mock(ClientCmdExecutor.class);

        objectMapper = new ObjectMapper(new MessagePackFactory());

        command = new CloseTaskSubscriptionCmdImpl(clientCmdExecutor, objectMapper);

        writeBuffer.wrap(BUFFER);
    }

    @Test
    public void shouldWriteRequest() throws JsonParseException, JsonMappingException, IOException
    {
        // given
        command
            .subscriptionId(2)
            .topicId(1)
            .taskType("foo");

        // when
        command.getRequestWriter().write(writeBuffer, 0);

        // then
        headerDecoder.wrap(writeBuffer, 0);

        assertThat(headerDecoder.schemaId()).isEqualTo(requestDecoder.sbeSchemaId());
        assertThat(headerDecoder.version()).isEqualTo(requestDecoder.sbeSchemaVersion());
        assertThat(headerDecoder.templateId()).isEqualTo(requestDecoder.sbeTemplateId());
        assertThat(headerDecoder.blockLength()).isEqualTo(requestDecoder.sbeBlockLength());

        requestDecoder.wrap(writeBuffer, headerDecoder.encodedLength(), requestDecoder.sbeBlockLength(), requestDecoder.sbeSchemaVersion());

        assertThat(requestDecoder.messageType()).isEqualTo(ControlMessageType.REMOVE_TASK_SUBSCRIPTION);

        final byte[] data = new byte[requestDecoder.dataLength()];
        requestDecoder.getData(data, 0, data.length);

        final TaskSubscription taskSubscription = objectMapper.readValue(data, TaskSubscription.class);

        assertThat(taskSubscription.getId()).isEqualTo(2L);
        assertThat(taskSubscription.getTopicId()).isEqualTo(1);
        assertThat(taskSubscription.getTaskType()).isEqualTo("foo");
    }

    @Test
    public void shouldBeNotValidIfSubscriptionIdIsNotSet()
    {
        command
            .topicId(1)
            .taskType("foo");

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("subscription id must be greater than or equal to 0");

        command.validate();
    }


    @Test
    public void shouldBeNotValidIfTopicIdIsNotSet()
    {
        command
            .subscriptionId(2)
            .taskType("foo");

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("topic id must be greater than or equal to 0");

        command.validate();
    }

    @Test
    public void shouldBeNotValidIfTaskTypeIsNotSet()
    {
        command
            .subscriptionId(2)
            .topicId(1);

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("task type must not be null");

        command.validate();
    }

}
