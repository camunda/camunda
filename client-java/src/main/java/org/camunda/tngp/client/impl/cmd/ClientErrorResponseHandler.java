package org.camunda.tngp.client.impl.cmd;

import org.agrona.DirectBuffer;
import org.camunda.tngp.client.cmd.BrokerRequestException;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
import org.camunda.tngp.protocol.clientapi.ErrorResponseDecoder;

public class ClientErrorResponseHandler
{
    protected ErrorResponseDecoder errorResponseDecoder = new ErrorResponseDecoder();

    public Throwable createException(final DirectBuffer responseBuffer, final int offset, final int blockLength, final int version)
    {
        errorResponseDecoder.wrap(responseBuffer, offset, blockLength, version);

        final ErrorCode errorCode = errorResponseDecoder.errorCode();
        final String errorData = errorResponseDecoder.errorData();

        return new BrokerRequestException(errorCode, errorData);
    }

}
