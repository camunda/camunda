package org.camunda.tngp.broker.services;

import uk.co.real_logic.agrona.concurrent.AtomicBuffer;
import uk.co.real_logic.agrona.concurrent.CountersManager;

public class Counters
{
    protected final CountersManager countersManager;

    protected final AtomicBuffer countersBuffer;

    public Counters(CountersManager countersManager, AtomicBuffer countersBuffer)
    {
        this.countersManager = countersManager;
        this.countersBuffer = countersBuffer;
    }

    public CountersManager getCountersManager()
    {
        return countersManager;
    }

    public AtomicBuffer getCountersBuffer()
    {
        return countersBuffer;
    }
}
