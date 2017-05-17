package org.camunda.tngp.transport.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.camunda.tngp.transport.util.SharedStateMachine.StateListener;

public class SharedStateMachineBlueprint<T>
{
    protected List<StateListener<T>> defaultStateListeners = new ArrayList<>();
    protected final ManyToOneRingBuffer channelStateChangeBuffer;
    protected int maxDynamicListeners = 8;

    public SharedStateMachineBlueprint(ManyToOneRingBuffer channelStateChangeBuffer)
    {
        this.channelStateChangeBuffer = channelStateChangeBuffer;
    }

    public SharedStateMachineBlueprint<T> copy()
    {
        final SharedStateMachineBlueprint<T> copy = new SharedStateMachineBlueprint<>(this.channelStateChangeBuffer);
        copy.defaultStateListeners = new ArrayList<>(defaultStateListeners);
        return copy;
    }

    public SharedStateMachineBlueprint<T> maxDynamicListeners(int maxDynamicListeners)
    {
        this.maxDynamicListeners = maxDynamicListeners;
        return this;
    }

    public SharedStateMachineBlueprint<T> onState(int state, Consumer<T> consumer)
    {
        final StateListener<T> listener = new StateListener<>();
        listener.interestSet = state;
        listener.callback = (s, o) -> consumer.accept(o);
        this.defaultStateListeners.add(listener);
        return this;
    }

    public SharedStateMachine<T> buildStateMachine(int id, T managedObject)
    {
        return new SharedStateMachine<>(
                id,
                managedObject,
                maxDynamicListeners,
                defaultStateListeners.toArray(new StateListener[defaultStateListeners.size()]),
                channelStateChangeBuffer);
    }
}
