package org.camunda.tngp.test.broker.protocol.clientapi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.io.DirectBufferInputStream;
import org.camunda.tngp.protocol.clientapi.ControlMessageResponseDecoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.test.broker.protocol.MsgPackHelper;
import org.camunda.tngp.util.buffer.BufferReader;

public class ControlMessageResponse implements BufferReader
{
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ControlMessageResponseDecoder responseDecoder = new ControlMessageResponseDecoder();

    protected MsgPackHelper msgPackHelper;
    protected Map<String, Object> data;

    public ControlMessageResponse(MsgPackHelper msgPackHelper)
    {
        this.msgPackHelper = msgPackHelper;
    }

    public Map<String, Object> getData()
    {
        return data;
    }

    @Override
    public void wrap(DirectBuffer responseBuffer, int offset, int length)
    {
        messageHeaderDecoder.wrap(responseBuffer, 0);

        if (messageHeaderDecoder.templateId() != responseDecoder.sbeTemplateId())
        {
            throw new RuntimeException("Unexpected response from broker.");
        }

        responseDecoder.wrap(responseBuffer, messageHeaderDecoder.encodedLength(), messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());

        final int dataLength = responseDecoder.dataLength();
        final int dataOffset = messageHeaderDecoder.encodedLength() + messageHeaderDecoder.blockLength() + ControlMessageResponseDecoder.dataHeaderLength();

        try (final InputStream is = new DirectBufferInputStream(responseBuffer, dataOffset, dataLength))
        {
            data = msgPackHelper.readMsgPack(is);
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }

    }
}
