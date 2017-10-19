package io.zeebe.gossip;

public class GossipConfiguration
{

    private int retransmissionMultiplier = 3;

    private int probeInterval = 100;
    private int probeTimeout = 500;
    private int probeIndirectNodes = 3;

    private int syncInterval = 10;
    private int syncNode = 1000;

    private int suspicionTimeout = 1000;

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

    /**
     * @return the interval until a suspected nodes is declared dead
     */
    public int getSuspicionTimeout()
    {
        return suspicionTimeout;
    }

    /**
     * The interval until a suspected nodes is declared dead.
     */
    public GossipConfiguration setSuspicionTimeout(final int suspicionTimeout)
    {
        this.suspicionTimeout = suspicionTimeout;
        return this;
    }

    @Override
    public String toString()
    {
        return "GossipConfiguration{" + "retransmitMultiplier=" + retransmissionMultiplier + ", probeInterval=" + probeInterval +
            ", probeTimeout=" + probeTimeout + ", probeIndirectNodes=" + probeIndirectNodes + ", syncInterval=" + syncInterval +
            ", syncNode=" + syncNode + ", suspicionTimeout=" + suspicionTimeout + '}';
    }

}
