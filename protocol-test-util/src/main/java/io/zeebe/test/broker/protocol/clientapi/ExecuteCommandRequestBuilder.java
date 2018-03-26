/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        return send()
                .await();
    }

    public ExecuteCommandRequest send()
    {
        return request.send();
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
        return eventType(EventType.WORKFLOW_INSTANCE_EVENT);
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
