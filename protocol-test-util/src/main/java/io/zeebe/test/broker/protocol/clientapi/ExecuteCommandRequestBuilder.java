package io.zeebe.test.broker.protocol.clientapi;

import java.util.Map;

import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.test.util.collection.MapBuilder;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.RemoteAddress;


public class ExecuteCommandRequestBuilder
{
    protected ExecuteCommandRequest request;

    public ExecuteCommandRequestBuilder(ClientOutput output, RemoteAddress target, MsgPackHelper msgPackHelper)
    {
        this.request = new ExecuteCommandRequest(output, target, msgPackHelper);
    }

    public ExecuteCommandResponse sendAndAwait()
    {
        return send().await();
    }

    public ExecuteCommandRequest send()
    {
        return request.send();
    }

    public ExecuteCommandRequestBuilder topicName(String topicName)
    {
        request.topicName(topicName);
        return this;
    }

    public ExecuteCommandRequestBuilder partitionId(int partitionId)
    {
        request.partitionId(partitionId);
        return this;
    }

    public ExecuteCommandRequestBuilder key(long key)
    {
        request.key(key);
        return this;
    }

    public ExecuteCommandRequestBuilder eventTypeTask()
    {
        return eventType(EventType.TASK_EVENT);
    }

    public ExecuteCommandRequestBuilder eventTypeWorkflow()
    {
        return eventType(EventType.WORKFLOW_EVENT);
    }


    public ExecuteCommandRequestBuilder eventType(EventType eventType)
    {
        request.eventType(eventType);
        return this;
    }

    public ExecuteCommandRequestBuilder eventTypeSubscription()
    {
        request.eventType(EventType.SUBSCRIPTION_EVENT);
        return this;
    }

    public ExecuteCommandRequestBuilder eventTypeSubscriber()
    {
        request.eventType(EventType.SUBSCRIBER_EVENT);
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
