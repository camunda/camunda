package org.camunda.tngp.client.cmd;

public class BrokerRequestException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public static final String ERROR_MESSAGE_FORMAT = "Request exception (%d): %s\n";

    protected final int errorCodeCode;
    protected final String errorMessage;

    public BrokerRequestException(int errorCode, String errorMessage)
    {
        super(String.format(ERROR_MESSAGE_FORMAT, errorCode, errorMessage));

        this.errorCodeCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public int getDetailCode()
    {
        return errorCodeCode;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }
}
