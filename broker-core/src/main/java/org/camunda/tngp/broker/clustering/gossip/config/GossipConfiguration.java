package org.camunda.tngp.broker.clustering.gossip.config;

public class GossipConfiguration
{
    public String[] initialContactPoints = new String[0];

    public int peerCapacity = 1000;

    public int peersStorageInterval = 1;
    public String peersStorageFile = "/tmp/tngp.cluster";

    public int disseminatorCapacity = 16;
    public int disseminationInterval = 1;
    public int disseminationTimeout = 10;

    public int failureDetectionCapacity = 8;
    public int failureDetectionProbeCapacity = 3;
    public int failureDetectorTimeout = 15;

    public int probeCapacity = 1;
    public int probeTimeout = 10;

    public int suspicionTimeout = 10;

    public int numClientChannelMax = disseminatorCapacity + (failureDetectionCapacity * failureDetectionProbeCapacity) + 1;
}
