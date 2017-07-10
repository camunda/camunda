/**
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

import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.selector.ConnectTransportPoller;

public class ClientConductor extends Conductor
{
    private final ConnectTransportPoller connectTransportPoller = new ConnectTransportPoller();

    public ClientConductor(ActorContext actorContext, TransportContext context)
    {
        super(actorContext, context);
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = super.doWork();

        workCount += connectTransportPoller.pollNow();

        return workCount;
    }

    public CompletableFuture<Void> requestClientChannel(int streamId)
    {
        return deferred.runAsync((f) ->
        {
            final RemoteAddress remoteAddress = remoteAddressList.getByStreamId(streamId);

            if (remoteAddress != null)
            {
                final TransportChannel ch = new TransportChannel(this,
                    remoteAddress,
                    transportContext.getMessageMaxLength(),
                    transportContext.getReceiveHandler());

                if (ch.beginConnect(f))
                {
                    connectTransportPoller.addChannel(ch);
                }
            }
            else
            {
                f.completeExceptionally(new RuntimeException(String.format("Unknown remote for streamId: %d", streamId)));
            }
        });
    }

}
