package org.camunda.tngp.util.state.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.camunda.tngp.util.state.concurrent.SharedStateMachine.StateListener;

public class SharedStateMachineBlueprint<T>
{
    protected List<StateListener<T>> defaultStateListeners = new ArrayList<>();
    protected int maxDynamicListeners = 8;
    protected AtomicInteger idCounter = new AtomicInteger(0);

    public SharedStateMachineBlueprint<T> copy()
    {
        final SharedStateMachineBlueprint<T> copy = new SharedStateMachineBlueprint<>();
        copy.defaultStateListeners = new ArrayList<>(defaultStateListeners);
        copy.idCounter = idCounter; // copying the reference is important to guarantee unique IDs for this blueprint and the copy
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

    public SharedStateMachine<T> buildStateMachine(T managedObject, SharedStateMachineManager<T> stateMachineManager)
    {
        return new SharedStateMachine<>(
                idCounter.incrementAndGet(),
                managedObject,
                maxDynamicListeners,
                defaultStateListeners.toArray(new StateListener[defaultStateListeners.size()]),
                stateMachineManager);
    }
}
