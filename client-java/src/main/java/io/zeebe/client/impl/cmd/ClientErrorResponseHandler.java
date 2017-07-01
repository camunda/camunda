package io.zeebe.client.impl.cmd;

import org.agrona.DirectBuffer;
import io.zeebe.client.cmd.BrokerRequestException;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;

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
