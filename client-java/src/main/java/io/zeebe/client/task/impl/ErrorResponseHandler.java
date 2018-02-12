package io.zeebe.client.task.impl;

import org.agrona.DirectBuffer;

import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.util.buffer.BufferUtil;

public class ErrorResponseHandler
{
    protected ErrorResponseDecoder decoder = new ErrorResponseDecoder();

    protected DirectBuffer errorMessage;

    public boolean handlesResponse(MessageHeaderDecoder responseHeader)
    {
        return ErrorResponseDecoder.SCHEMA_ID == responseHeader.schemaId() &&
                ErrorResponseDecoder.TEMPLATE_ID == responseHeader.templateId();
    }

    public void wrap(DirectBuffer body, int offset, int length, int version)
    {
        decoder.wrap(body, offset, length, version);

        final int errorDataLength = decoder.errorDataLength();
        final byte[] errorData = new byte[errorDataLength];
        decoder.getErrorData(errorData, 0, errorDataLength);
        this.errorMessage = BufferUtil.wrapArray(errorData);
    }

    public ErrorCode getErrorCode()
    {
        return decoder.errorCode();
    }

    public DirectBuffer getErrorMessage()
    {
        return errorMessage;
    }
}
