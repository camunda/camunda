package org.camunda.tngp.test.broker.protocol.clientapi;

import java.util.Map;

import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.test.util.collection.MapBuilder;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public class ExecuteCommandRequestBuilder
{
    protected ExecuteCommandRequest request;

    public ExecuteCommandRequestBuilder(TransportConnectionPool connectionPool, int channelId, MsgPackHelper msgPackHelper)
    {
        this.request = new ExecuteCommandRequest(connectionPool, channelId, msgPackHelper);
    }

    public ExecuteCommandResponse sendAndAwait()
    {
        return send().await();
    }

    public ExecuteCommandRequest send()
    {
        return request.send();
    }

    public ExecuteCommandRequestBuilder topicId(int topicId)
    {
        request.topicId(topicId);
        return this;
    }

    public ExecuteCommandRequestBuilder eventTypeTask()
    {
        request.eventType(EventType.TASK_EVENT);
        return this;
    }

    public ExecuteCommandRequestBuilder command(Map<String, Object> command)
    {
        request.command(command);
        return this;
    }

    public MapBuilder<ExecuteCommandRequestBuilder> command()
    {
        final MapBuilder<ExecuteCommandRequestBuilder> mapBuilder = new MapBuilder<>(this, this::command);
        return mapBuilder;
    }

}
