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
package io.zeebe.client.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.cmd.Request;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.util.sched.future.ActorFuture;

public abstract class ControlMessageRequest<R> implements Request<R>
{

    protected final ControlMessageType type;
    protected final String targetTopic;
    protected int targetPartition;
    protected final Class<? extends R> responseClass;

    protected final RequestManager client;

    /**
     * Constructor for requests addressing a specific partition
     */
    public ControlMessageRequest(RequestManager client, ControlMessageType type,
            int targetPartition, Class<? extends R> responseClass)
    {
        this(client, type, null, targetPartition, responseClass);
    }

    /**
     * Constructor for requests addressing a specific topic, but unspecified partition
     */
    public ControlMessageRequest(RequestManager client, ControlMessageType type,
            String targetTopic, Class<? extends R> responseClass)
    {
        this(client, type, targetTopic, -1, responseClass);
    }

    /**
     * Constructor for requests addressing any broker
     */
    public ControlMessageRequest(RequestManager client, ControlMessageType type, Class<? extends R> responseClass)
    {
        this(client, type, null, -1, responseClass);
    }

    private ControlMessageRequest(RequestManager client, ControlMessageType type,
            String targetTopic, int targetPartition, Class<? extends R> responseClass)
    {
        this.client = client;
        this.type = type;
        this.targetTopic = targetTopic;
        this.targetPartition = targetPartition;
        this.responseClass = responseClass;
    }

    @JsonIgnore
    public ControlMessageType getType()
    {
        return type;
    }

    @JsonIgnore
    public String getTargetTopic()
    {
        return targetTopic;
    }

    @JsonIgnore
    public int getTargetPartition()
    {
        return targetPartition;
    }

    public void setTargetPartition(int targetPartition)
    {
        this.targetPartition = targetPartition;
    }

    @JsonIgnore
    public Class<? extends R> getResponseClass()
    {
        return responseClass;
    }

    public void onResponse(R response)
    {
    }

    public abstract Object getRequest();

    @Override
    public R execute()
    {
        return client.execute(this);
    }

    @Override
    public ActorFuture<R> executeAsync()
    {
        return client.executeAsync(this);
    }

    public ZeebeFuture<R> send()
    {
        // TODO remove cast to zeebe future
        return (ZeebeFuture<R>) client.executeAsync(this);
    }

}
