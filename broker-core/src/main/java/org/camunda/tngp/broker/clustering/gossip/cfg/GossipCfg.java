package org.camunda.tngp.broker.clustering.gossip.cfg;

public class GossipCfg
{
    public String[] initialContactPoints = new String[0];

    public int maxPeerCapacity = 1000;

    public int peersStorageInterval = 1;
    public String peersStorageFile = "/tmp/tngp.cluster";

    public int numDisseminators = 32;
    public int disseminationInterval = 1;
    public int disseminationTimeout = 10;

    public int numFailureDetectors = 32;
    public int numProbersPerFailureDetector = 4;
    public int failureDetectorTimeout = 20;

    public int numProbers = 32;
    public int probeTimeout = 10;

    public int suspicionTimeout = 10;

    public int numClientChannelMax = numDisseminators + (numFailureDetectors * numProbersPerFailureDetector) + 1;
}
