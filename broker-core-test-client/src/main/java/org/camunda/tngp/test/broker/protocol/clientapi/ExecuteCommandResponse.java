package org.camunda.tngp.test.broker.protocol.clientapi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.io.DirectBufferInputStream;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandResponseDecoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

public class ExecuteCommandResponse implements BufferReader
{
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ExecuteCommandResponseDecoder responseDecoder = new ExecuteCommandResponseDecoder();

    protected final MsgPackHelper msgPackHelper;

    protected Map<String, Object> event;

    public ExecuteCommandResponse(MsgPackHelper msgPackHelper)
    {
        this.msgPackHelper = msgPackHelper;
    }

    public Map<String, Object> getEvent()
    {
        return event;
    }

    public long key()
    {
        return responseDecoder.longKey();
    }

    public long topicId()
    {
        return responseDecoder.topicId();
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

        final int eventLength = responseDecoder.eventLength();
        final int eventOffset = messageHeaderDecoder.encodedLength() + messageHeaderDecoder.blockLength() + ExecuteCommandResponseDecoder.eventHeaderLength();

        try (final InputStream is = new DirectBufferInputStream(responseBuffer, eventOffset, eventLength))
        {
            event = msgPackHelper.readMsgPack(is);
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

}
