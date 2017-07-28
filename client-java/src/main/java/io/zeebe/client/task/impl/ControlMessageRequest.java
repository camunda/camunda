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
package io.zeebe.client.task.impl;

import java.util.concurrent.Future;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.cmd.Request;
import io.zeebe.client.impl.Partition;
import io.zeebe.protocol.clientapi.ControlMessageType;

public abstract class ControlMessageRequest<R> implements Request<R>
{

    protected final ControlMessageType type;
    protected final Partition target;
    protected final Class<R> responseClass;

    protected final RequestManager client;

    public ControlMessageRequest(RequestManager client, ControlMessageType type, Partition target, Class<R> responseClass)
    {
        this.client = client;
        this.type = type;
        this.target = target;
        this.responseClass = responseClass;
    }

    @JsonIgnore
    public ControlMessageType getType()
    {
        return type;
    }

    @JsonIgnore
    public Partition getTarget()
    {
        return target;
    }

    @JsonIgnore
    public Class<R> getResponseClass()
    {
        return responseClass;
    }

    public abstract Object getRequest();

    @Override
    public R execute()
    {
        return client.execute(this);
    }

    @Override
    public Future<R> executeAsync()
    {
        return client.executeAsync(this);
    }

}
