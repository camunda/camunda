package io.zeebe.test.broker.protocol.clientapi;

import java.util.Map;

import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.test.util.collection.MapBuilder;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.RemoteAddress;

public class ControlMessageRequestBuilder
{
    protected ControlMessageRequest request;

    public ControlMessageRequestBuilder(ClientOutput output, RemoteAddress target, MsgPackHelper msgPackHelper)
    {
        request = new ControlMessageRequest(output, target, msgPackHelper);
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
