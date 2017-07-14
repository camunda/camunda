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
package io.zeebe.transport.impl;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.actor.ActorReference;

public class TransportContext
{
    private int messageMaxLength;
    private long channelKeepAlivePeriod = 0L;

    private ServerOutput serverOutput;
    private ClientOutput clientOutput;

    private Dispatcher receiveBuffer;

    private Subscription senderSubscription;

    private RemoteAddressList remoteAddressList;

    private ClientRequestPool clientRequestPool;

    private ActorReference[] actorReferences;

    private FragmentHandler receiveHandler;
    private FragmentHandler sendFailureHandler;

    private ServerSocketBinding serverSocketBinding;

    public int getMessageMaxLength()
    {
        return messageMaxLength;
    }

    public void setMessageMaxLength(int messageMaxLength)
    {
        this.messageMaxLength = messageMaxLength;
    }

    public ServerOutput getServerOutput()
    {
        return serverOutput;
    }

    public void setServerOutput(ServerOutput serverOutput)
    {
        this.serverOutput = serverOutput;
    }

    public ClientOutput getClientOutput()
    {
        return clientOutput;
    }

    public void setClientOutput(ClientOutput clientOutput)
    {
        this.clientOutput = clientOutput;
    }

    public Dispatcher getReceiveBuffer()
    {
        return receiveBuffer;
    }

    public void setReceiveBuffer(Dispatcher receiveBuffer)
    {
        this.receiveBuffer = receiveBuffer;
    }

    public Subscription getSenderSubscription()
    {
        return senderSubscription;
    }

    public void setSenderSubscription(Subscription senderSubscription)
    {
        this.senderSubscription = senderSubscription;
    }

    public RemoteAddressList getRemoteAddressList()
    {
        return remoteAddressList;
    }

    public void setRemoteAddressList(RemoteAddressList remoteAddressList)
    {
        this.remoteAddressList = remoteAddressList;
    }

    public ClientRequestPool getClientRequestPool()
    {
        return clientRequestPool;
    }

    public void setClientRequestPool(ClientRequestPool clientRequestPool)
    {
        this.clientRequestPool = clientRequestPool;
    }

    public void setActorReferences(ActorReference... conductorReferences)
    {
        this.actorReferences = conductorReferences;
    }

    public ActorReference[] getActorReferences()
    {
        return actorReferences;
    }

    public void setReceiveHandler(FragmentHandler receiveHandler)
    {
        this.receiveHandler = receiveHandler;
    }

    public FragmentHandler getReceiveHandler()
    {
        return receiveHandler;
    }

    public FragmentHandler getSendFailureHandler()
    {
        return sendFailureHandler;
    }

    public void setSendFailureHandler(FragmentHandler sendFailureHandler)
    {
        this.sendFailureHandler = sendFailureHandler;
    }

    public ServerSocketBinding getServerSocketBinding()
    {
        return serverSocketBinding;
    }

    public void setServerSocketBinding(ServerSocketBinding serverSocketBinding)
    {
        this.serverSocketBinding = serverSocketBinding;
    }

    public void setChannelKeepAlivePeriod(long channelKeepAlivePeriod)
    {
        this.channelKeepAlivePeriod = channelKeepAlivePeriod;
    }

    public long getChannelKeepAlivePeriod()
    {
        return channelKeepAlivePeriod;
    }
}
