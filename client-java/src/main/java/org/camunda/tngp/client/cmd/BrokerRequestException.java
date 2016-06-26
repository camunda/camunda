package org.camunda.tngp.client.cmd;

public class BrokerRequestException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public static final String ERROR_MESSAGE_FORMAT = "Failed request (%d-%d): %s";

    protected final int componentCode;
    protected final int detailCode;

    public BrokerRequestException(int componentCode, int detailCode, String message)
    {
        super(String.format(ERROR_MESSAGE_FORMAT, componentCode, detailCode, message));
        this.componentCode = componentCode;
        this.detailCode = detailCode;
    }

    public int getComponentCode()
    {
        return componentCode;
    }

    public int getDetailCode()
    {
        return detailCode;
    }
}
