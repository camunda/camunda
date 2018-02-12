package io.zeebe.test.broker.protocol.brokerapi;

public abstract class AbstractMessageBuilder<T> implements MessageBuilder<T>
{

    protected Runnable beforeResponse;

    @Override
    public void beforeResponse()
    {
        if (beforeResponse != null)
        {
            beforeResponse.run();
        }
    }

    public void beforeResponse(Runnable beforeResponse)
    {
        this.beforeResponse = beforeResponse;
    }


}
