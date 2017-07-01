package io.zeebe.test.broker.protocol.clientapi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.io.DirectBufferInputStream;
import io.zeebe.protocol.clientapi.ControlMessageResponseDecoder;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.util.buffer.BufferReader;

public class ControlMessageResponse implements BufferReader
{
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ControlMessageResponseDecoder responseDecoder = new ControlMessageResponseDecoder();
    protected final ErrorResponse errorResponse;

    protected MsgPackHelper msgPackHelper;
    protected Map<String, Object> data;

    public ControlMessageResponse(MsgPackHelper msgPackHelper)
    {
        this.msgPackHelper = msgPackHelper;
        this.errorResponse = new ErrorResponse(msgPackHelper);
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
            if (messageHeaderDecoder.templateId() == ErrorResponseDecoder.TEMPLATE_ID)
            {
                errorResponse.wrap(responseBuffer, offset, length);
                throw new RuntimeException("Unexpected error response from broker: " +
                        errorResponse.getErrorCode() + " - " + errorResponse.getErrorData());
            }
            else
            {
                throw new RuntimeException("Unexpected response from broker. Template id " + messageHeaderDecoder.templateId());
            }
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
