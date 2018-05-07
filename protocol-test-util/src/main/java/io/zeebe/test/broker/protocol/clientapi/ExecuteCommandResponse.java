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
package io.zeebe.test.broker.protocol.clientapi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.io.DirectBufferInputStream;

import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.util.buffer.BufferReader;

public class ExecuteCommandResponse implements BufferReader
{
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ExecuteCommandResponseDecoder responseDecoder = new ExecuteCommandResponseDecoder();
    protected final ErrorResponse errorResponse;

    protected final MsgPackHelper msgPackHelper;

    protected Map<String, Object> value;

    public ExecuteCommandResponse(MsgPackHelper msgPackHelper)
    {
        this.msgPackHelper = msgPackHelper;
        this.errorResponse = new ErrorResponse(msgPackHelper);
    }

    public Map<String, Object> getValue()
    {
        return value;
    }

    public long position()
    {
        return responseDecoder.position();
    }

    public long key()
    {
        return responseDecoder.key();
    }

    public int partitionId()
    {
        return responseDecoder.partitionId();
    }

    public ValueType valueType()
    {
        return responseDecoder.valueType();
    }

    public Intent intent()
    {
        return Intent.fromProtocolValue(responseDecoder.valueType(), responseDecoder.intent());
    }

    public RecordType recordType()
    {
        return responseDecoder.recordType();
    }

    @Override
    public void wrap(DirectBuffer responseBuffer, int offset, int length)
    {
        messageHeaderDecoder.wrap(responseBuffer, offset);

        if (messageHeaderDecoder.templateId() != responseDecoder.sbeTemplateId())
        {
            if (messageHeaderDecoder.templateId() == ErrorResponseDecoder.TEMPLATE_ID)
            {
                errorResponse.wrap(responseBuffer, offset + messageHeaderDecoder.encodedLength(), length);
                throw new RuntimeException("Unexpected error response from broker: " +
                        errorResponse.getErrorCode() + " - " + errorResponse.getErrorData());
            }
            else
            {
                throw new RuntimeException("Unexpected response from broker. Template id " + messageHeaderDecoder.templateId());
            }
        }

        responseDecoder.wrap(responseBuffer, offset + messageHeaderDecoder.encodedLength(), messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());

        final int eventLength = responseDecoder.valueLength();
        final int eventOffset = responseDecoder.limit() + ExecuteCommandResponseDecoder.valueHeaderLength();

        try (InputStream is = new DirectBufferInputStream(responseBuffer, eventOffset, eventLength))
        {
            value = msgPackHelper.readMsgPack(is);
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

}
