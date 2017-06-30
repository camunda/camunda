package io.zeebe.servicecontainer.impl;

import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.concurrent.IdleStrategy;

public class WaitingIdleStrategy implements IdleStrategy
{
    protected int idleCount = 0;
    protected AtomicBoolean isWaiting = new AtomicBoolean();

    @Override
    public void idle(int workCount)
    {
        if (workCount == 0)
        {
            if (idleCount > 10000)
            {
                idle();
            }
            else
            {
                ++idleCount;
                Thread.yield();
            }
        }
        else
        {
            idleCount = 0;
        }
    }

    @Override
    public void idle()
    {
        try
        {
            synchronized (this)
            {
                isWaiting.set(true);
                wait(1000);
            }
        }
        catch (InterruptedException e)
        {
            // ignore
        }
    }

    @Override
    public void reset()
    {

    }

    public void signalWorkAvailable()
    {
        if (isWaiting.compareAndSet(true, false))
        {
            synchronized (this)
            {
                notify();
            }
        }
    }
}
