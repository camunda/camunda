package org.camunda.tngp.transport.requestresponse.client;

public class RequestTimeoutException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public RequestTimeoutException()
    {
        super("Request timed out.");
    }

}
