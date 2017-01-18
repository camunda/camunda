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
import org.camunda.tngp.client.impl.cmd.ClientResponseHandler;
import org.camunda.tngp.client.impl.cmd.CreateTaskCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEvent;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEventType;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandRequestDecoder;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandResponseEncoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.junit.Before;
import org.junit.Test;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CreateTaskCmdTest
{
    private static final byte[] BUFFER = new byte[1014 * 1024];

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final ExecuteCommandRequestDecoder requestDecoder = new ExecuteCommandRequestDecoder();
    private final ExecuteCommandResponseEncoder responseEncoder = new ExecuteCommandResponseEncoder();

    private UnsafeBuffer writeBuffer = new UnsafeBuffer(0, 0);

    private CreateTaskCmdImpl createTaskCommand;
    private ObjectMapper objectMapper;

    @Before
    public void setup()
    {
        final ClientCmdExecutor clientCmdExecutor = mock(ClientCmdExecutor.class);

        objectMapper = new ObjectMapper(new MessagePackFactory());

        createTaskCommand = new CreateTaskCmdImpl(clientCmdExecutor, objectMapper);

        writeBuffer.wrap(BUFFER);
    }

    @Test
    public void shouldWriteHeader()
    {
        createTaskCommand.getRequestWriter().write(writeBuffer, 0);

        headerDecoder.wrap(writeBuffer, 0);

        assertThat(headerDecoder.schemaId()).isEqualTo(requestDecoder.sbeSchemaId());
        assertThat(headerDecoder.version()).isEqualTo(requestDecoder.sbeSchemaVersion());
        assertThat(headerDecoder.templateId()).isEqualTo(requestDecoder.sbeTemplateId());
        assertThat(headerDecoder.blockLength()).isEqualTo(requestDecoder.sbeBlockLength());
    }

    @Test
    public void shouldWriteRequest() throws JsonParseException, JsonMappingException, IOException
    {
        // given
        createTaskCommand
            .taskQueueId(1)
            .taskType("foo")
            .payload("bar");

        // when
        createTaskCommand.getRequestWriter().write(writeBuffer, 0);

        // then
        requestDecoder.wrap(writeBuffer, headerDecoder.encodedLength(), requestDecoder.sbeBlockLength(), requestDecoder.sbeSchemaVersion());

        assertThat(requestDecoder.topicId()).isEqualTo(1L);

        final byte[] command = new byte[requestDecoder.commandLength()];
        requestDecoder.getCommand(command, 0, command.length);

        final TaskEvent taskEvent = objectMapper.readValue(command, TaskEvent.class);

        assertThat(taskEvent.getEvent()).isEqualTo(TaskEventType.CREATE);
        assertThat(taskEvent.getType()).isEqualTo("foo");
        assertThat(taskEvent.getPayload()).isEqualTo("bar".getBytes());
    }

    @Test
    public void shouldReadResponse() throws JsonProcessingException
    {
        final ClientResponseHandler<Long> responseHandler = createTaskCommand.getResponseHandler();

        assertThat(responseHandler.getResponseSchemaId()).isEqualTo(responseEncoder.sbeSchemaId());
        assertThat(responseHandler.getResponseTemplateId()).isEqualTo(responseEncoder.sbeTemplateId());

        responseEncoder.wrap(writeBuffer, 0);

        // given
        final TaskEvent taskEvent = new TaskEvent();
        taskEvent.setEvent(TaskEventType.CREATED);
        taskEvent.setType("foo");
        taskEvent.setPayload("bar".getBytes());

        final byte[] jsonEvent = objectMapper.writeValueAsBytes(taskEvent);

        responseEncoder
            .topicId(1L)
            .longKey(2L)
            .bytesKey("")
            .putEvent(jsonEvent, 0, jsonEvent.length);

        // when
        final Long taskKey = responseHandler.readResponse(writeBuffer, 0, responseEncoder.encodedLength());

        // then
        assertThat(taskKey).isEqualTo(2L);
    }

}
