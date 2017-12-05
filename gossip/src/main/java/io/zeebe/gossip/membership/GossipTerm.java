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
package io.zeebe.gossip.membership;

import io.zeebe.util.time.ClockUtil;

public class GossipTerm
{
    private long epoch;
    private long heartbeat;

    public GossipTerm()
    {
        epoch = ClockUtil.getCurrentTimeInMillis();
        heartbeat = 0;
    }

    public long getEpoch()
    {
        return epoch;
    }

    public GossipTerm epoch(long epoch)
    {
        this.epoch = epoch;
        return this;
    }

    public long getHeartbeat()
    {
        return heartbeat;
    }

    public GossipTerm heartbeat(long heartBeat)
    {
        this.heartbeat = heartBeat;
        return this;
    }

    public void increment()
    {
        heartbeat += 1;
    }

    public boolean isGreaterThan(GossipTerm otherTerm)
    {
        return epoch > otherTerm.epoch || (epoch == otherTerm.epoch && heartbeat > otherTerm.heartbeat);
    }

    public boolean isEqual(GossipTerm otherTerm)
    {
        return epoch == otherTerm.epoch && heartbeat == otherTerm.heartbeat;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("GossipTerm [epoch=");
        builder.append(epoch);
        builder.append(", heartBeat=");
        builder.append(heartbeat);
        builder.append("]");
        return builder.toString();
    }
}
