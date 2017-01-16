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
package org.camunda.tngp.client.impl.cmd;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandRequestEncoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.util.LangUtil;
import org.camunda.tngp.util.buffer.RequestWriter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ExecuteCommandRequestWriter implements RequestWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandRequestEncoder commandRequestEncoder = new ExecuteCommandRequestEncoder();

    protected final CommandRequestWriter requestWriter;
    protected final ObjectMapper objectMapper;

    public ExecuteCommandRequestWriter(CommandRequestWriter requestWriter, ObjectMapper objectMapper)
    {
        this.requestWriter = requestWriter;
        this.objectMapper = objectMapper;
    }

    protected byte[] command;

    @Override
    public void validate()
    {
        requestWriter.validate();
    }

    @Override
    public int getLength()
    {
        ensureCommandInitialized();

        return TransportHeaderDescriptor.headerLength() +
                RequestResponseProtocolHeaderDescriptor.headerLength() +
                headerEncoder.encodedLength() +
                commandRequestEncoder.sbeBlockLength() +
                command.length;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        ensureCommandInitialized();

        headerEncoder.wrap(buffer, offset)
            .blockLength(commandRequestEncoder.sbeBlockLength())
            .schemaId(commandRequestEncoder.sbeSchemaId())
            .templateId(commandRequestEncoder.sbeTemplateId())
            .version(commandRequestEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        commandRequestEncoder.wrap(buffer, offset);

        commandRequestEncoder
            .topicId(requestWriter.getTopicId())
            .putCommand(command, 0, command.length);

        reset();
    }

    protected void reset()
    {
        requestWriter.reset();
        command = null;
    }

    private void ensureCommandInitialized()
    {
        if (command == null)
        {
            try
            {
                command = objectMapper.writeValueAsBytes(requestWriter.writeCommand());
            }
            catch (JsonProcessingException e)
            {
                LangUtil.rethrowUnchecked(e);
            }
        }
    }

}
