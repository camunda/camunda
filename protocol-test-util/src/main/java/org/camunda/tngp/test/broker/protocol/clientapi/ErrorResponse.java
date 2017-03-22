package org.camunda.tngp.test.broker.protocol.clientapi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.io.DirectBufferInputStream;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
import org.camunda.tngp.protocol.clientapi.ErrorResponseDecoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.test.broker.protocol.MsgPackHelper;
import org.camunda.tngp.util.buffer.BufferReader;

public class ErrorResponse implements BufferReader
{
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ErrorResponseDecoder bodyDecoder = new ErrorResponseDecoder();

    protected final MsgPackHelper msgPackHelper;

    protected Map<String, Object> failedRequest;
    protected String errorData;

    public ErrorResponse(MsgPackHelper msgPackHelper)
    {
        this.msgPackHelper = msgPackHelper;
    }

    public ErrorCode getErrorCode()
    {
        return bodyDecoder.errorCode();
    }

    public String getErrorData()
    {
        return errorData;
    }

    public Map<String, Object> getFailedRequest()
    {
        return failedRequest;
    }

    @Override
    public void wrap(DirectBuffer responseBuffer, int offset, int length)
    {
        messageHeaderDecoder.wrap(responseBuffer, 0);

        if (messageHeaderDecoder.templateId() != bodyDecoder.sbeTemplateId())
        {
            throw new RuntimeException("Unexpected response from broker.");
        }

        bodyDecoder.wrap(responseBuffer, messageHeaderDecoder.encodedLength(), messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());

        final int errorDataLength = bodyDecoder.errorDataLength();
        final int errorDataOffset = messageHeaderDecoder.encodedLength() + messageHeaderDecoder.blockLength() + ErrorResponseDecoder.errorDataHeaderLength();

        errorData = responseBuffer.getStringWithoutLengthUtf8(errorDataOffset, errorDataLength);

        bodyDecoder.limit(errorDataOffset + errorDataLength);

        final int failedRequestLength = bodyDecoder.failedRequestLength();
        final int failedRequestOffset = bodyDecoder.limit() + ErrorResponseDecoder.failedRequestHeaderLength();

        try (final InputStream is = new DirectBufferInputStream(responseBuffer, failedRequestOffset, failedRequestLength))
        {
            failedRequest = msgPackHelper.readMsgPack(is);
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }
}
