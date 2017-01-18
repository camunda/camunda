package org.camunda.tngp.client.cmd;

public class BrokerRequestException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public static final String ERROR_MESSAGE_FORMAT = "Request exception (%d): %s\n";

    protected final int detailCode;
    protected final String message;

    public BrokerRequestException(int detailCode, String message)
    {
        super(String.format(ERROR_MESSAGE_FORMAT, detailCode, message));

        this.detailCode = detailCode;
        this.message = message;
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
}
