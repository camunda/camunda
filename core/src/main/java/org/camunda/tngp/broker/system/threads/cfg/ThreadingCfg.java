package org.camunda.tngp.broker.system.threads.cfg;

public class ThreadingCfg
{
    public static enum BrokerIdleStrategy
    {
        BACKOFF, BUSY_SPIN;
    }

    public int numberOfThreads = 1;
    public int maxIdleTimeMs = 200;
    public BrokerIdleStrategy idleStrategy = BrokerIdleStrategy.BACKOFF;
}
