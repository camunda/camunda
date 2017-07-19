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
package io.zeebe.transport.impl.selector;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.ToIntFunction;

import org.agrona.LangUtil;
import org.agrona.nio.TransportPoller;

import io.zeebe.transport.ChannelConnectTimeoutException;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.util.time.ClockUtil;

public class ConnectTransportPoller extends TransportPoller
{
    protected final ToIntFunction<SelectionKey> processKeyFn = this::processKey;

    protected int channelCount = 0;
    protected Queue<TransportChannel> connectingChannels = new ArrayDeque<>();

    protected final boolean useConnectTimeout;
    protected final long connectTimeout;
    protected final String timeoutFailureMessage;

    public ConnectTransportPoller(long connectTimeout)
    {
        this.useConnectTimeout = connectTimeout > 0;
        this.connectTimeout = connectTimeout;
        this.timeoutFailureMessage = "Could not connect socket channel in " +
                connectTimeout + " milliseconds. Aborting connect attempt.";
    }

    public int doWork()
    {
        int workCount = pollNow();
        if (useConnectTimeout)
        {
            workCount += enforceConnectTimeout();
        }

        return workCount;
    }

    public int enforceConnectTimeout()
    {
        final long currentTimeoutCutoff = ClockUtil.getCurrentTimeInMillis() - connectTimeout;

        boolean currentChannelResolved;
        int numChannelsResolved = 0;

        while (!connectingChannels.isEmpty())
        {
            final TransportChannel nextChannel = connectingChannels.peek();
            currentChannelResolved = false;

            if (nextChannel.isConnecting())
            {
                if (nextChannel.hasBegunConnectingBefore(currentTimeoutCutoff))
                {
                    nextChannel.failConnect(new ChannelConnectTimeoutException(timeoutFailureMessage));
                    currentChannelResolved = true;
                }
            }
            else
            {
                currentChannelResolved = true;
            }

            if (currentChannelResolved)
            {
                connectingChannels.poll();
                numChannelsResolved++;
            }
            else
            {
                break;
            }

        }

        return numChannelsResolved;
    }

    public int pollNow()
    {
        int workCount = 0;

        if (channelCount > 0 && selector.isOpen())
        {

            try
            {
                selector.selectNow();
                workCount = selectedKeySet.forEach(processKeyFn);
            }
            catch (IOException e)
            {
                selectedKeySet.reset();
                LangUtil.rethrowUnchecked(e);
            }
        }
        return workCount;
    }

    protected int processKey(SelectionKey key)
    {
        if (key != null)
        {
            final TransportChannel channel = (TransportChannel) key.attachment();
            removeChannel(channel);
            channel.finishConnect();

            return 1;
        }

        return 0;
    }

    public void addChannel(TransportChannel channel)
    {
        channel.registerSelector(selector, SelectionKey.OP_CONNECT);
        if (useConnectTimeout)
        {
            connectingChannels.add(channel);
        }

        ++channelCount;
    }

    public void removeChannel(TransportChannel channel)
    {
        channel.removeSelector(selector);
        --channelCount;
    }

}
