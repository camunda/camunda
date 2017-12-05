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

    private int retransmissionMultiplier = 5;

    private int probeInterval = 1_000;
    private int probeTimeout = 500;
    private int probeIndirectNodes = 3;
    private int probeIndirectTimeout = 1_000;

    private int syncInterval = 10;
    private int syncNode = 1000;
    private int syncTimeout = 3_000;

    private int suspicionMultiplier = 5;

    private int joinTimeout = 1_000;
    private int joinInterval = 5_000;


    public int getJoinTimeout()
    {
        return joinTimeout;
    }

    public GossipConfiguration joinTimeout(int joinTimeout)
    {
        this.joinTimeout = joinTimeout;
        return this;
    }

    public int getJoinInterval()
    {
        return joinInterval;
    }

    public GossipConfiguration joinInterval(int joinInterval)
    {
        this.joinInterval = joinInterval;
        return this;
    }

    /**
     * @return The multiplier for the number of retransmissions of a gossip message.
     *          The number of retransmits is calculated as {@code retransmissionMultiplier} * log(n + 1),
     *          where n is the number of nodes in the cluster
     */
    public int getRetransmissionMultiplier()
    {
        return retransmissionMultiplier;
    }

    /**
     * The multiplier for the number of retransmissions of a gossip message.
     * The number of retransmits is calculated as {@code retransmissionMultiplier} * log(n + 1),
     * where n is the number of nodes in the cluster
     */
    public GossipConfiguration setRetransmissionMultiplier(final int retransmissionMultiplier)
    {
        this.retransmissionMultiplier = retransmissionMultiplier;
        return this;
    }

    /**
     * @return the interval of probes in milliseconds.
     */
    public int getProbeInterval()
    {
        return probeInterval;
    }

    /**
     * The interval of probes in milliseconds.
     */
    public GossipConfiguration setProbeInterval(final int probeInterval)
    {
        this.probeInterval = probeInterval;
        return this;
    }

    /**
     * @return the timeout of a probe in milliseconds.
     */
    public int getProbeTimeout()
    {
        return probeTimeout;
    }

    /**
     * The timeout of a probe in milliseconds.
     */
    public GossipConfiguration setProbeTimeout(final int probeTimeout)
    {
        this.probeTimeout = probeTimeout;
        return this;
    }

    /**
     * @return the number of nodes to send indirect probes
     */
    public int getProbeIndirectNodes()
    {
        return probeIndirectNodes;
    }

    /**
     * The number of nodes to send indirect probes.
     */
    public GossipConfiguration setProbeIndirectNodes(final int probeIndirectNodes)
    {
        this.probeIndirectNodes = probeIndirectNodes;
        return this;
    }

    public int getProbeIndirectTimeout()
    {
        return probeIndirectTimeout;
    }

    public GossipConfiguration probeIndirectTimeout(int probeIndirectTimeout)
    {
        this.probeIndirectTimeout = probeIndirectTimeout;
        return this;
    }

    public int getSyncTimeout()
    {
        return syncTimeout;
    }

    public GossipConfiguration syncTimeout(int syncTimeout)
    {
        this.syncTimeout = syncTimeout;
        return this;
    }

    /**
     * @return the interval of syncs between nodes in milliseconds
     */
    public int getSyncInterval()
    {
        return syncInterval;
    }

    /**
     * The interval of syncs between nodes in milliseconds.
     */
    public GossipConfiguration setSyncInterval(final int syncInterval)
    {
        this.syncInterval = syncInterval;
        return this;
    }

    /**
     * @return the number of nodes to sync with
     */
    public int getSyncNode()
    {
        return syncNode;
    }

    /**
     * @return The number of nodes to sync with.
     */
    public GossipConfiguration setSyncNode(final int syncNode)
    {
        this.syncNode = syncNode;
        return this;
    }


    public int getSuspicionMultiplier()
    {
        return suspicionMultiplier;
    }


    public GossipConfiguration setSuspicionMultiplier(final int suspicionMultiplier)
    {
        this.suspicionMultiplier = suspicionMultiplier;
        return this;
    }

    @Override
    public String toString()
    {
        return "GossipConfiguration{" + "retransmitMultiplier=" + retransmissionMultiplier + ", probeInterval=" + probeInterval +
            ", probeTimeout=" + probeTimeout + ", probeIndirectNodes=" + probeIndirectNodes + ", syncInterval=" + syncInterval +
            ", syncNode=" + syncNode + ", suspicionMultiplier=" + suspicionMultiplier + '}';
    }

}
