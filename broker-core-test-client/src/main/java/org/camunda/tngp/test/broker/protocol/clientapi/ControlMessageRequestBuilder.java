package org.camunda.tngp.test.broker.protocol.clientapi;

import java.util.Map;

import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public class ControlMessageRequestBuilder
{
    protected ControlMessageRequest request;

    public ControlMessageRequestBuilder(TransportConnectionPool connectionPool, int channelId, MsgPackHelper msgPackHelper)
    {
        request = new ControlMessageRequest(connectionPool, channelId, msgPackHelper);
    }

    public ControlMessageRequest send()
    {
        return request.send();
    }

    public ControlMessageResponse sendAndAwait()
    {
        return send().await();
    }

    public ControlMessageRequestBuilder messageType(ControlMessageType msgType)
    {
        request.messageType(msgType);
        return this;
    }

    public ControlMessageRequestBuilder data(Map<String, Object> data)
    {
        request.data(data);
        return this;
    }

    public MapBuilder<ControlMessageRequestBuilder> data()
    {
        return new MapBuilder<>(this, this::data);
    }
}
