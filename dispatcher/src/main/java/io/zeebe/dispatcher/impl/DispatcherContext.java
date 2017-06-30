package io.zeebe.dispatcher.impl;

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.actor.ActorReference;

public class DispatcherContext
{
    protected ManyToOneConcurrentArrayQueue<DispatcherConductorCommand> dispatcherCommandQueue = new ManyToOneConcurrentArrayQueue<>(100);

    protected Actor conductor;
    protected ActorReference conductorRef;

    public ManyToOneConcurrentArrayQueue<DispatcherConductorCommand> getDispatcherCommandQueue()
    {
        return dispatcherCommandQueue;
    }

    public Actor getConductor()
    {
        return conductor;
    }

    public void setConductor(Actor conductorAgent)
    {
        this.conductor = conductorAgent;
    }

    public void setConductorReference(ActorReference conductorRef)
    {
        this.conductorRef = conductorRef;
    }

    public ActorReference getConductorReference()
    {
        return conductorRef;
    }
}