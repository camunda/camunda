package io.zeebe.broker.services;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.status.CountersManager;

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
