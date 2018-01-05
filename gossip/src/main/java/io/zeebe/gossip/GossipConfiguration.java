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

public class GossipConfiguration
{
    private int retransmissionMultiplier = 3;

    private int probeInterval = 1_000;
    private int probeTimeout = 500;

    private int probeIndirectNodes = 3;
    private int probeIndirectTimeout = 1_000;

    private int suspicionMultiplier = 5;

    private int syncTimeout = 3_000;

    private int joinTimeout = 1_000;
    private int joinInterval = 5_000;

    private int leaveTimeout = 1_000;

    private int maxMembershipEventsPerMessage = 32;
    private int maxCustomEventsPerMessage = 8;

    private int subscriptionPollLimit = 3;

    /**
     * The timeout of a join request in milliseconds.
     */
    public int getJoinTimeout()
    {
        return joinTimeout;
    }

    /**
     * The timeout of a join request in milliseconds.
     */
    public GossipConfiguration joinTimeout(int joinTimeout)
    {
        this.joinTimeout = joinTimeout;
        return this;
    }

    /**
     * The time in milliseconds when a failed join request is send again.
     */
    public int getJoinInterval()
    {
        return joinInterval;
    }

    /**
     * The time in milliseconds when a failed join request is send again.
     */
    public GossipConfiguration joinInterval(int joinInterval)
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
     * The time in milliseconds between two probe requests.
     */
    public int getProbeInterval()
    {
        return probeInterval;
    }

    /**
     * The time in milliseconds between two probe requests.
     */
    public GossipConfiguration setProbeInterval(final int probeInterval)
    {
        this.probeInterval = probeInterval;
        return this;
    }

    /**
     * The timeout of a probe request in milliseconds.
     */
    public int getProbeTimeout()
    {
        return probeTimeout;
    }

    /**
     * The timeout of a probe request in milliseconds.
     */
    public GossipConfiguration setProbeTimeout(final int probeTimeout)
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
     * The timeout of an indirect probe request in milliseconds.
     */
    public int getProbeIndirectTimeout()
    {
        return probeIndirectTimeout;
    }

    /**
     * The timeout of an indirect probe request in milliseconds.
     */
    public GossipConfiguration probeIndirectTimeout(int probeIndirectTimeout)
    {
        this.probeIndirectTimeout = probeIndirectTimeout;
        return this;
    }

    /**
     * The timeout of a sync request in milliseconds.
     */
    public int getSyncTimeout()
    {
        return syncTimeout;
    }

    /**
     * The timeout of a sync request in milliseconds.
     */
    public GossipConfiguration syncTimeout(int syncTimeout)
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
     * The timeout of a leave request in milliseconds.
     */
    public int getLeaveTimeout()
    {
        return leaveTimeout;
    }

    /**
     * The timeout of a leave request in milliseconds.
     */
    public GossipConfiguration leaveTimeout(int leaveTimeout)
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

    /**
     * The maximum amount of messages which are polled from the transport
     * subscription at once.
     */
    public int getSubscriptionPollLimit()
    {
        return subscriptionPollLimit;
    }

    /**
     * The maximum amount of messages which are polled from the transport
     * subscription at once.
     */
    public GossipConfiguration subscriptionPollLimit(int limit)
    {
        this.subscriptionPollLimit = limit;
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
        builder.append(", subscriptionPollLimit=");
        builder.append(subscriptionPollLimit);
        builder.append("]");
        return builder.toString();
    }

}
