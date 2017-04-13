package org.camunda.tngp.broker.system.threads.cfg;

import org.camunda.tngp.broker.system.ComponentConfiguration;

public class ThreadingCfg extends ComponentConfiguration
{
    public enum BrokerIdleStrategy
    {
        BACKOFF, BUSY_SPIN;
    }

    public int numberOfThreads = -1;
    public int maxIdleTimeMs = 200;
    public BrokerIdleStrategy idleStrategy = BrokerIdleStrategy.BACKOFF;
}
