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
package io.zeebe.client.impl.cmd;

import java.util.concurrent.Future;

import io.zeebe.client.cmd.Request;
import io.zeebe.client.event.Event;
import io.zeebe.client.event.impl.EventImpl;
import io.zeebe.client.impl.RequestManager;

public abstract class CommandImpl<E extends Event> implements Request<E>
{

    protected final RequestManager client;

    public CommandImpl(RequestManager client)
    {
        this.client = client;
    }

    @Override
    public E execute()
    {
        return client.execute(this);
    }

    @Override
    public Future<E> executeAsync()
    {
        return client.executeAsync(this);
    }

    public String generateError(E requestEvent, E responseEvent)
    {
        final long requestEventKey = requestEvent.getMetadata().getKey();
        final StringBuilder sb = new StringBuilder();
        sb.append("Command ");

        if (requestEventKey >= 0)
        {
            sb.append("for event with key ");
            sb.append(requestEventKey);
            sb.append(" ");
        }

        sb.append("was rejected by broker (");
        sb.append(responseEvent.getState());
        sb.append(")");

        return sb.toString();
    }

    public abstract EventImpl getEvent();

    public abstract String getExpectedStatus();
}
