package io.zeebe.client.cmd;

import io.zeebe.protocol.clientapi.ErrorCode;

public class BrokerRequestException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public static final String ERROR_MESSAGE_FORMAT = "Request exception (%s): %s\n";

    protected final ErrorCode errorCode;
    protected final String errorMessage;

    public BrokerRequestException(final ErrorCode errorCode, final String errorMessage)
    {
        super(String.format(ERROR_MESSAGE_FORMAT, errorCode, errorMessage));

        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public ErrorCode getErrorCode()
    {
        return errorCode;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }
}
