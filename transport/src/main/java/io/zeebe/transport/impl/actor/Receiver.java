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
package io.zeebe.transport.impl.actor;

import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.selector.ReadTransportPoller;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.Actor;

public class Receiver implements Actor
{
    protected final DeferredCommandContext commandContext;
    protected final ReadTransportPoller transportPoller;

    public Receiver(ActorContext actorContext, TransportContext context)
    {
        this.commandContext = new DeferredCommandContext();
        this.transportPoller = new ReadTransportPoller();

        actorContext.setReceiver(this);
    }

    @Override
    public int doWork() throws Exception
    {
        int work = 0;

        work += commandContext.doWork();
        work += transportPoller.pollNow();

        return work;
    }

    public void closeSelectors()
    {
        transportPoller.close();
    }

    @Override
    public String name()
    {
        return "receiver";
    }

    public void removeChannel(TransportChannel c)
    {
        commandContext.runAsync(() ->
        {
            transportPoller.removeChannel(c);
        });
    }

    public void registerChannel(TransportChannel c)
    {
        commandContext.runAsync((future) ->
        {
            transportPoller.addChannel(c);
        });
    }

}
