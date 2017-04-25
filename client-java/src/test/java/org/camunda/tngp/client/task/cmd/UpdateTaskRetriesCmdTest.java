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
package org.camunda.tngp.client.task.cmd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;
import static org.camunda.tngp.util.VarDataUtil.readBytes;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.ClientResponseHandler;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEvent;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEventType;
import org.camunda.tngp.client.impl.cmd.taskqueue.UpdateTaskRetriesCmdImpl;
import org.camunda.tngp.client.impl.data.MsgPackConverter;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandRequestDecoder;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandResponseEncoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class UpdateTaskRetriesCmdTest
{
    protected static final int TOPIC_ID = 1;
    private static final byte[] BUFFER = new byte[1014 * 1024];

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final ExecuteCommandRequestDecoder requestDecoder = new ExecuteCommandRequestDecoder();
    private final ExecuteCommandResponseEncoder responseEncoder = new ExecuteCommandResponseEncoder();
    private final MsgPackConverter msgPackConverter = new MsgPackConverter();

    private final UnsafeBuffer writeBuffer = new UnsafeBuffer(0, 0);

    private UpdateTaskRetriesCmdImpl command;
    private ObjectMapper objectMapper;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup()
    {
        final ClientCmdExecutor clientCmdExecutor = mock(ClientCmdExecutor.class);

        objectMapper = new ObjectMapper(new MessagePackFactory());

        command = new UpdateTaskRetriesCmdImpl(clientCmdExecutor, objectMapper, TOPIC_ID);

        writeBuffer.wrap(BUFFER);
    }

    @Test
    public void shouldWriteRequest() throws JsonParseException, JsonMappingException, IOException
    {
        // given
        final Map<String, Object> headers = new HashMap<>();
        headers.put("a", "b");
        headers.put("c", "d");

        command
            .taskType("foo")
            .retries(2)
            .headers(headers)
            .payload("{ \"bar\" : 4 }");

        // when
        command.getRequestWriter().write(writeBuffer, 0);

        // then
        headerDecoder.wrap(writeBuffer, 0);

        assertThat(headerDecoder.schemaId()).isEqualTo(requestDecoder.sbeSchemaId());
        assertThat(headerDecoder.version()).isEqualTo(requestDecoder.sbeSchemaVersion());
        assertThat(headerDecoder.templateId()).isEqualTo(requestDecoder.sbeTemplateId());
        assertThat(headerDecoder.blockLength()).isEqualTo(requestDecoder.sbeBlockLength());

        requestDecoder.wrap(writeBuffer, headerDecoder.encodedLength(), requestDecoder.sbeBlockLength(), requestDecoder.sbeSchemaVersion());

        assertThat(requestDecoder.eventType()).isEqualTo(TASK_EVENT);
        assertThat(requestDecoder.topicId()).isEqualTo(TOPIC_ID);

        final byte[] command = readBytes(requestDecoder::getCommand, requestDecoder::commandLength);

        final TaskEvent taskEvent = objectMapper.readValue(command, TaskEvent.class);

        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.UPDATE_RETRIES);
        assertThat(taskEvent.getType()).isEqualTo("foo");
        assertThat(taskEvent.getRetries()).isEqualTo(2);
        assertThat(taskEvent.getHeaders()).hasSize(2).containsEntry("a", "b").containsEntry("c", "d");
        assertThat(taskEvent.getPayload()).isEqualTo(msgPackConverter.convertToMsgPack("{ \"bar\" : 4 }"));
    }

    @Test
    public void shouldReadSuccessfulResponse() throws JsonProcessingException
    {
        final ClientResponseHandler<Long> responseHandler = command.getResponseHandler();

        assertThat(responseHandler.getResponseSchemaId()).isEqualTo(responseEncoder.sbeSchemaId());
        assertThat(responseHandler.getResponseTemplateId()).isEqualTo(responseEncoder.sbeTemplateId());

        responseEncoder.wrap(writeBuffer, 0);

        // given
        final TaskEvent taskEvent = new TaskEvent();
        taskEvent.setEventType(TaskEventType.RETRIES_UPDATED);

        final byte[] jsonEvent = objectMapper.writeValueAsBytes(taskEvent);

        responseEncoder
            .key(2L)
            .putEvent(jsonEvent, 0, jsonEvent.length);

        // when
        final Long taskKey = responseHandler.readResponse(0, writeBuffer, 0, responseEncoder.sbeBlockLength(), responseEncoder.sbeSchemaVersion());

        // then
        assertThat(taskKey).isEqualTo(2L);
    }

    @Test
    public void shouldReadFailedResponse() throws JsonProcessingException
    {
        final ClientResponseHandler<Long> responseHandler = command.getResponseHandler();

        assertThat(responseHandler.getResponseSchemaId()).isEqualTo(responseEncoder.sbeSchemaId());
        assertThat(responseHandler.getResponseTemplateId()).isEqualTo(responseEncoder.sbeTemplateId());

        responseEncoder.wrap(writeBuffer, 0);

        // given
        final TaskEvent taskEvent = new TaskEvent();
        taskEvent.setEventType(TaskEventType.UPDATE_RETRIES_REJECTED);

        final byte[] jsonEvent = objectMapper.writeValueAsBytes(taskEvent);

        responseEncoder
            .key(2L)
            .putEvent(jsonEvent, 0, jsonEvent.length);

        // when
        final Long taskKey = responseHandler.readResponse(0, writeBuffer, 0, responseEncoder.sbeBlockLength(), responseEncoder.sbeSchemaVersion());

        // then
        assertThat(taskKey).isEqualTo(-1L);
    }

    @Test
    public void shouldBeNotValidIfTaskKeyIsNotSet()
    {
        command
            .taskType("foo")
            .retries(2);

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("task key must be greater than or equal to 0");

        command.validate();
    }

    @Test
    public void shouldBeNotValidIfTaskTypeIsNotSet()
    {
        command
            .taskKey(2L)
            .retries(2);

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("task type must not be null");

        command.validate();
    }

    @Test
    public void shouldBeNotValidIRetriesAreNotSet()
    {
        command
            .taskKey(2L)
            .taskType("foo");

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("retries must be greater than or equal to 0");

        command.validate();
    }

}
