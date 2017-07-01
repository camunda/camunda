package io.zeebe.test.broker.protocol.clientapi;

import static io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder.eventHeaderLength;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.io.DirectBufferInputStream;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.util.buffer.BufferReader;

public class ExecuteCommandResponse implements BufferReader
{
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ExecuteCommandResponseDecoder responseDecoder = new ExecuteCommandResponseDecoder();
    protected final ErrorResponse errorResponse;

    protected final MsgPackHelper msgPackHelper;

    protected String topicName;
    protected Map<String, Object> event;

    public ExecuteCommandResponse(MsgPackHelper msgPackHelper)
    {
        this.msgPackHelper = msgPackHelper;
        this.errorResponse = new ErrorResponse(msgPackHelper);
    }

    public Map<String, Object> getEvent()
    {
        return event;
    }

    public long key()
    {
        return responseDecoder.key();
    }

    public String getTopicName()
    {
        return topicName;
    }

    public int partitionId()
    {
        return responseDecoder.partitionId();
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

        topicName = responseDecoder.topicName();

        final int eventLength = responseDecoder.eventLength();
        final int eventOffset = responseDecoder.limit() + eventHeaderLength();

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
