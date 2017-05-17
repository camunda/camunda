package org.camunda.tngp.transport.util;

import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;

/**
 * One central state machine manager that records and dispatches events for all state machines.
 */
public class SharedStateMachineManager<T>
{

    protected UnsafeBuffer tempBuf = new UnsafeBuffer(new byte[BitUtil.SIZE_OF_INT]);
    protected static final int DISPATCHES_PER_ITERATION = 32;

    protected Int2ObjectHashMap<SharedStateMachine<T>> stateMachines = new Int2ObjectHashMap<>();
    protected ManyToOneRingBuffer transitionBuffer;

    public SharedStateMachineManager(ManyToOneRingBuffer transitionBuffer)
    {
        this.transitionBuffer = transitionBuffer;
    }

    public void onStateMachineCreation(SharedStateMachine<T> stateMachine)
    {
        stateMachines.put(stateMachine.getId(), stateMachine);
    }

    public void onStateMachineDisposal(SharedStateMachine<T> stateMachine)
    {
        stateMachines.remove(stateMachine.getId());
    }

    public int dispatchTransitionEvents()
    {
        return transitionBuffer.read(this::dispatchTransitionEvent, DISPATCHES_PER_ITERATION);
    }

    protected void dispatchTransitionEvent(int stateMachineId, MutableDirectBuffer buffer, int offset, int length)
    {
        final SharedStateMachine<T> stateMachine = stateMachines.get(stateMachineId);

        if (stateMachine != null)
        {
            final int newState = buffer.getInt(offset);
            stateMachine.dispatchTransitionEvent(newState);
        }
    }
}
