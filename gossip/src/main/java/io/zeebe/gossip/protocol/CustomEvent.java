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
package io.zeebe.gossip.protocol;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.gossip.membership.GossipTerm;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.collection.Reusable;
import org.agrona.*;
import org.agrona.concurrent.UnsafeBuffer;

public class CustomEvent implements Reusable
{
    private final GossipTerm senderGossipTerm = new GossipTerm();
    private final SocketAddress senderAddress = new SocketAddress();

    private final MutableDirectBuffer typeBuffer = new ExpandableArrayBuffer();
    private final DirectBuffer typeView = new UnsafeBuffer(typeBuffer);
    private int typeLength = 0;

    private final MutableDirectBuffer payloadBuffer = new ExpandableArrayBuffer();
    private final DirectBuffer payloadView = new UnsafeBuffer(payloadBuffer);
    private int payloadLength = 0;

    public void typeLength(int length)
    {
        this.typeLength = length;
    }

    public MutableDirectBuffer getTypeBuffer()
    {
        return typeBuffer;
    }

    public void payloadLength(int length)
    {
        this.payloadLength = length;
    }

    public MutableDirectBuffer getPayloadBuffer()
    {
        return payloadBuffer;
    }

    public CustomEvent senderAddress(SocketAddress address)
    {
        this.senderAddress.wrap(address);
        return this;
    }

    public CustomEvent senderGossipTerm(GossipTerm term)
    {
        this.senderGossipTerm.wrap(term);
        return this;
    }

    public CustomEvent type(DirectBuffer typeBuffer)
    {
        this.typeLength = typeBuffer.capacity();
        this.typeBuffer.putBytes(0, typeBuffer, 0, typeLength);
        return this;
    }

    public CustomEvent payload(DirectBuffer payloadBuffer)
    {
        return payload(payloadBuffer, 0, payloadBuffer.capacity());
    }

    public CustomEvent payload(DirectBuffer payloadBuffer, int offset, int length)
    {
        this.payloadLength = length;
        this.payloadBuffer.putBytes(0, payloadBuffer, offset, length);
        return this;
    }

    public GossipTerm getSenderGossipTerm()
    {
        return senderGossipTerm;
    }

    public SocketAddress getSenderAddress()
    {
        return senderAddress;
    }

    public DirectBuffer getType()
    {
        typeView.wrap(typeBuffer, 0, typeLength);
        return typeView;
    }

    public DirectBuffer getPayload()
    {
        payloadView.wrap(payloadBuffer, 0, payloadLength);
        return payloadView;
    }

    public int getTypeLength()
    {
        return typeLength;
    }

    public int getPayloadLength()
    {
        return payloadLength;
    }

    @Override
    public void reset()
    {
        senderGossipTerm.epoch(0L).heartbeat(0L);
        senderAddress.reset();

        typeLength(0);
        payloadLength(0);
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("CustomEvent [senderAddress=");
        builder.append(senderAddress);
        builder.append(", senderGossipTerm=");
        builder.append(senderGossipTerm);
        builder.append(", type=");
        builder.append(bufferAsString(typeView, 0, typeLength));
        builder.append(", payload=");
        builder.append(bufferAsString(payloadView, 0, payloadLength));
        builder.append("]");
        return builder.toString();
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((senderAddress == null) ? 0 : senderAddress.hashCode());
        result = prime * result + ((senderGossipTerm == null) ? 0 : senderGossipTerm.hashCode());
        result = prime * result + ((payloadView == null) ? 0 : payloadView.hashCode());
        result = prime * result + ((typeView == null) ? 0 : typeView.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        final CustomEvent other = (CustomEvent) obj;
        if (senderAddress == null)
        {
            if (other.senderAddress != null)
            {
                return false;
            }
        }
        else if (!senderAddress.equals(other.senderAddress))
        {
            return false;
        }
        if (senderGossipTerm == null)
        {
            if (other.senderGossipTerm != null)
            {
                return false;
            }
        }
        else if (!senderGossipTerm.equals(other.senderGossipTerm))
        {
            return false;
        }
        if (typeView == null)
        {
            if (other.typeView != null)
            {
                return false;
            }
        }
        else if (!BufferUtil.equals(typeView, other.typeView))
        {
            return false;
        }
        if (payloadView == null)
        {
            if (other.payloadView != null)
            {
                return false;
            }
        }
        else if (!BufferUtil.equals(payloadView, other.payloadView))
        {
            return false;
        }
        return true;
    }

}
