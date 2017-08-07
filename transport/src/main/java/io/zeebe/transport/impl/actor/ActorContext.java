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

import java.util.concurrent.CompletableFuture;

import io.zeebe.transport.TransportListener;
import io.zeebe.transport.impl.TransportChannel;

public abstract class ActorContext
{
    private Conductor conductor;
    private Sender sender;
    private Receiver receiver;

    public void setConductor(Conductor clientConductor)
    {
        this.conductor = clientConductor;
    }

    public void setSender(Sender sender)
    {
        this.sender = sender;
    }

    public void setReceiver(Receiver receiver)
    {
        this.receiver = receiver;
    }

    public void registerChannel(TransportChannel ch)
    {
        sender.registerChannel(ch);
        receiver.registerChannel(ch);
    }

    public void removeChannel(TransportChannel ch)
    {
        sender.removeChannel(ch);
        receiver.removeChannel(ch);
    }

    public void removeListener(TransportListener listener)
    {
        conductor.removeListener(listener);
    }

    public CompletableFuture<Void> registerListener(TransportListener channelListener)
    {
        return conductor.registerListener(channelListener);
    }

    public CompletableFuture<Void> onClose()
    {
        return  conductor.onClose()
                         .whenComplete((v, t) -> receiver.closeSelectors());
    }

    public CompletableFuture<Void> closeAllOpenChannels()
    {
        return conductor.closeCurrentChannels();
    }

    public CompletableFuture<Void> interruptAllChannels()
    {
        return conductor.interruptAllChannels();
    }

}
