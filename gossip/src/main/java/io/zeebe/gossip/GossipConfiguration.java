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
package io.zeebe.gossip;

import java.time.Duration;

public class GossipConfiguration
{
    private int retransmissionMultiplier = 3;

    private long probeInterval = 1000;
    private long probeTimeout = 500;

    private int probeIndirectNodes = 3;
    private long probeIndirectTimeout = 1000;

    private int suspicionMultiplier = 5;

    private long syncTimeout = 3000;

    private long joinTimeout = 1000;
    private long joinInterval = 1000;

    private long leaveTimeout = 1000;

    private int maxMembershipEventsPerMessage = 32;
    private int maxCustomEventsPerMessage = 8;

    /**
     * The timeout of a join request.
     */
    public long getJoinTimeout()
    {
        return joinTimeout;
    }

    public Duration getJoinTimeoutDurtion()
    {
        return Duration.ofMillis(joinTimeout);
    }

    /**
     * The timeout of a join request.
     */
    public GossipConfiguration setJoinTimeout(long joinTimeout)
    {
        this.joinTimeout = joinTimeout;
        return this;
    }

    /**
     * The time when a failed join request is send again.
     */
    public long getJoinInterval()
    {
        return joinInterval;
    }

    public Duration getJoinIntervalDuration()
    {
        return Duration.ofMillis(joinInterval);
    }

    /**
     * The time when a failed join request is send again.
     */
    public GossipConfiguration setJoinInterval(long joinInterval)
    {
        this.joinInterval = joinInterval;
        return this;
    }

    /**
     * The multiplier for the number of retransmissions of a gossip message. The
     * number of retransmits is calculated as
     * {@code retransmissionMultiplier * log(n + 1)}, where n is the number of
     * nodes in the cluster.
     */
    public int getRetransmissionMultiplier()
    {
        return retransmissionMultiplier;
    }

    /**
     * The multiplier for the number of retransmissions of a gossip message. The
     * number of retransmits is calculated as
     * {@code retransmissionMultiplier * log(n + 1)}, where n is the number of
     * nodes in the cluster.
     */
    public GossipConfiguration setRetransmissionMultiplier(final int retransmissionMultiplier)
    {
        this.retransmissionMultiplier = retransmissionMultiplier;
        return this;
    }

    /**
     * The time between two probe requests.
     */
    public long getProbeInterval()
    {
        return probeInterval;
    }

    /**
     * The time between two probe requests.
     */
    public GossipConfiguration setProbeInterval(final long probeInterval)
    {
        this.probeInterval = probeInterval;
        return this;
    }

    /**
     * The timeout of a probe request.
     */
    public long getProbeTimeout()
    {
        return probeTimeout;
    }

    public Duration getProbeTimeoutDuration()
    {
        return Duration.ofMillis(probeTimeout);
    }

    /**
     * The timeout of a probe request.
     */
    public GossipConfiguration setProbeTimeout(final long probeTimeout)
    {
        this.probeTimeout = probeTimeout;
        return this;
    }

    /**
     * The number of nodes to send an indirect probe request to.
     */
    public int getProbeIndirectNodes()
    {
        return probeIndirectNodes;
    }

    /**
     * The number of nodes to send an indirect probe request to.
     */
    public GossipConfiguration setProbeIndirectNodes(final int probeIndirectNodes)
    {
        this.probeIndirectNodes = probeIndirectNodes;
        return this;
    }

    /**
     * The timeout of an indirect probe request.
     */
    public long getProbeIndirectTimeout()
    {
        return probeIndirectTimeout;
    }

    public Duration getProbeIndirectTimeoutDuration()
    {
        return Duration.ofMillis(probeIndirectTimeout);
    }

    /**
     * The timeout of an indirect probe request.
     */
    public GossipConfiguration probeIndirectTimeout(long probeIndirectTimeout)
    {
        this.probeIndirectTimeout = probeIndirectTimeout;
        return this;
    }

    /**
     * The timeout of a sync request.
     */
    public long getSyncTimeout()
    {
        return syncTimeout;
    }

    public Duration getSyncTimeoutDuration()
    {
        return Duration.ofMillis(syncTimeout);
    }

    /**
     * The timeout of a sync request.
     */
    public GossipConfiguration syncTimeout(long syncTimeout)
    {
        this.syncTimeout = syncTimeout;
        return this;
    }

    /**
     * The multiplier for the suspicion timeout of a node. The suspicion timeout
     * of a node is calculated as
     * {@code suspicionMultiplier * log(n + 1) * probeInterval}, where n is the
     * number of nodes in the cluster.
     */
    public int getSuspicionMultiplier()
    {
        return suspicionMultiplier;
    }

    /**
     * The multiplier for the suspicion timeout of a node. The suspicion timeout
     * of a node is calculated as
     * {@code suspicionMultiplier * log(n + 1) * probeInterval}, where n is the
     * number of nodes in the cluster.
     */
    public GossipConfiguration setSuspicionMultiplier(final int suspicionMultiplier)
    {
        this.suspicionMultiplier = suspicionMultiplier;
        return this;
    }

    /**
     * The timeout of a leave request.
     */
    public long getLeaveTimeout()
    {
        return leaveTimeout;
    }

    public Duration getLeaveTimeoutDuration()
    {
        return Duration.ofMillis(leaveTimeout);
    }

    /**
     * The timeout of a leave request.
     */
    public GossipConfiguration setLeaveTimeout(long leaveTimeout)
    {
        this.leaveTimeout = leaveTimeout;
        return this;
    }

    /**
     * The maximum amount of membership evens in a gossip message.
     */
    public int getMaxMembershipEventsPerMessage()
    {
        return maxMembershipEventsPerMessage;
    }

    /**
     * The maximum amount of membership evens in a gossip message.
     */
    public GossipConfiguration maxMembershipEventsPerMessage(int maxMembershipEventsPerMessage)
    {
        this.maxMembershipEventsPerMessage = maxMembershipEventsPerMessage;
        return this;
    }

    /**
     * The maximum amount of custom evens in a gossip message.
     */
    public int getMaxCustomEventsPerMessage()
    {
        return maxCustomEventsPerMessage;
    }

    /**
     * The maximum amount of custom evens in a gossip message.
     */
    public GossipConfiguration maxCustomEventsPerMessage(int maxCustomEventsPerMessage)
    {
        this.maxCustomEventsPerMessage = maxCustomEventsPerMessage;
        return this;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("GossipConfiguration [retransmissionMultiplier=");
        builder.append(retransmissionMultiplier);
        builder.append(", probeInterval=");
        builder.append(probeInterval);
        builder.append(", probeTimeout=");
        builder.append(probeTimeout);
        builder.append(", probeIndirectNodes=");
        builder.append(probeIndirectNodes);
        builder.append(", probeIndirectTimeout=");
        builder.append(probeIndirectTimeout);
        builder.append(", suspicionMultiplier=");
        builder.append(suspicionMultiplier);
        builder.append(", syncTimeout=");
        builder.append(syncTimeout);
        builder.append(", joinTimeout=");
        builder.append(joinTimeout);
        builder.append(", joinInterval=");
        builder.append(joinInterval);
        builder.append(", leaveTimeout=");
        builder.append(leaveTimeout);
        builder.append(", maxMembershipEventsPerMessage=");
        builder.append(maxMembershipEventsPerMessage);
        builder.append(", maxCustomEventsPerMessage=");
        builder.append(maxCustomEventsPerMessage);
        builder.append("]");
        return builder.toString();
    }

    public Duration getProbeIntervalDuration()
    {
        return Duration.ofMillis(probeInterval);
    }

}
