package org.camunda.tngp.client.cmd;

public class BrokerRequestException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public static final String ERROR_MESSAGE_FORMAT = "Request exception (%d): %s\nFailed request: %s";

    protected final int detailCode;
    protected final String message;
    protected final String failedRequest;

    public BrokerRequestException(int detailCode, String message, String failedRequest)
    {
        super(String.format(ERROR_MESSAGE_FORMAT, detailCode, message, failedRequest));

        this.detailCode = detailCode;
        this.message = message;
        this.failedRequest = failedRequest;
    }

    public int getDetailCode()
    {
        return detailCode;
    }

    @Override
    public String getMessage()
    {
        return message;
    }

    public String getFailedRequest()
    {
        return failedRequest;
    }
}
