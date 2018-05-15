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

import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.record.Record;
import io.zeebe.client.cmd.Request;
import io.zeebe.client.impl.record.RecordImpl;
import io.zeebe.util.sched.future.ActorFuture;

public abstract class CommandImpl<R extends Record> implements Request<R>
{

    protected final RequestManager client;

    public CommandImpl(RequestManager client)
    {
        this.client = client;
    }

    @Override
    public R execute()
    {
        // TODO remove execute
        return client.execute(this);
    }

    @Override
    public ActorFuture<R> executeAsync()
    {
        // TODO remove executeAsync
        return (ActorFuture<R>) client.send(this);
    }

    public ZeebeFuture<R> send()
    {
        return client.send(this);
    }

    public String generateError(Record command, String reason)
    {
        final long requestEventKey = command.getMetadata().getKey();
        final StringBuilder sb = new StringBuilder();
        sb.append("Command ");

        if (requestEventKey >= 0)
        {
            sb.append("for event with key ");
            sb.append(requestEventKey);
            sb.append(" ");
        }

        sb.append("was rejected by broker (");
        sb.append(command.getMetadata().getIntent());
        sb.append(")");

        return sb.toString();
    }

    public abstract RecordImpl getCommand();

}
