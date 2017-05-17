package org.camunda.tngp.transport.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.BitUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.camunda.tngp.transport.PooledFuture;

public class SharedStateMachine<T>
{

    protected final int id;
    protected final T statefulObject;
    protected final StateListener<T>[] staticListeners;
    protected final StateListener<T>[] dynamicListeners;
    protected final ObjectPool<StateListener<T>> pool;

    // state management
    protected volatile int state;
    protected final AtomicBoolean stateChanging = new AtomicBoolean(false);
    protected final ManyToOneRingBuffer stateChangeBuffer;

    protected final AtomicBoolean listenerMutex = new AtomicBoolean(false);
    protected final UnsafeBuffer writeBuf = new UnsafeBuffer(new byte[BitUtil.SIZE_OF_INT]);

    public SharedStateMachine(
            int id,
            T statefulObject,
            int listenerCapacity,
            StateListener<T>[] staticListeners,
            ManyToOneRingBuffer stateChangeBuffer)
    {
        this.id = id;
        this.staticListeners = staticListeners;
        this.statefulObject = statefulObject;
        this.dynamicListeners = new StateListener[listenerCapacity];
        this.pool = new ObjectPool<>(listenerCapacity, StateListener::new);
        this.stateChangeBuffer = stateChangeBuffer;
    }

    public void listenFor(int interestSet, IntObjectBiConsumer<T> listener)
    {
        final StateListener<T> stateListener = this.pool.request();
        stateListener.interestSet = interestSet;
        stateListener.callback = listener;

        registerListenerOrReturnToPool(stateListener);
    }

    public void listenFor(int expectedState, int exceptionalState, CompletableFuture<? super T> future)
    {
        final StateListener<T> stateListener = this.pool.request();

        stateListener.interestSet = expectedState;
        if (exceptionalState > 0)
        {
            stateListener.interestSet |= exceptionalState;
        }

        stateListener.callback = (state, obj) ->
        {
            if (state == expectedState)
            {
                future.complete(obj);
            }
            else
            {
                future.cancel(false);
            }
        };

        registerListenerOrReturnToPool(stateListener);
    }

    public void listenFor(int expectedStateMask, int exceptionalStateMask, PooledFuture<? super T> future)
    {
        final StateListener<T> stateListener = this.pool.request();
        stateListener.interestSet = expectedStateMask | exceptionalStateMask;
        stateListener.future = future;
        stateListener.successSet = expectedStateMask;

        registerListenerOrReturnToPool(stateListener);
    }

    public void dispatchTransitionEvent(int newState)
    {
        while (!listenerMutex.compareAndSet(false, true))
        {
        }

        try
        {
            dispatchEventToListeners(staticListeners, newState, false);
            dispatchEventToListeners(dynamicListeners, newState, true);
        }
        finally
        {
            listenerMutex.set(false);
        }
    }

    protected void dispatchEventToListeners(StateListener<T>[] listeners, int newState, boolean discardListener)
    {
        for (int i = 0; i < listeners.length; i++)
        {
            final StateListener<T> stateListener = listeners[i];

            if (stateListener != null && (stateListener.interestSet & newState) != 0)
            {
                stateListener.invoke(newState, statefulObject);
                if (discardListener)
                {
                    listeners[i] = null;
                }
            }
        }
    }

    public boolean makeStateTransition(int newState)
    {
        return makeStateTransition(-1, newState);
    }

    public boolean makeStateTransition(int expectedStateMask, int newState)
    {
        while (!stateChanging.compareAndSet(false, true))
        {
        }

        try
        {
            final boolean canEnterState;
            if (expectedStateMask >= 0)
            {
                canEnterState = (state & expectedStateMask) != 0;
            }
            else
            {
                canEnterState = true;
            }

            if (canEnterState)
            {
                state = newState;
                writeBuf.putInt(0, newState);
                final boolean stateChangeRecorded = stateChangeBuffer.write(id, writeBuf, 0, BitUtil.SIZE_OF_INT);
                if (!stateChangeRecorded)
                {
                    throw new RuntimeException("Could not record state change for " + statefulObject);
                }
            }

            return canEnterState;
        }
        finally
        {
            stateChanging.set(false);
        }

    }

    public int getCurrentState()
    {
        return state;
    }

    public boolean isInState(int state)
    {
        return this.state == state;
    }

    public boolean isInAnyState(int stateMask)
    {
        return (this.state & stateMask) != 0;
    }

    public int getId()
    {
        return id;
    }

    protected boolean registerListener(StateListener listener)
    {
        while (!listenerMutex.compareAndSet(false, true))
        {
        }

        try
        {
            for (int i = 0; i < dynamicListeners.length; i++)
            {
                if (dynamicListeners[i] == null)
                {
                    dynamicListeners[i] = listener;
                    return true;
                }
            }

            return false;
        }
        finally
        {
            listenerMutex.set(false);
        }
    }

    protected void registerListenerOrReturnToPool(StateListener listener)
    {
        final boolean listenerAdded = registerListener(listener);
        if (!listenerAdded)
        {
            listener.release();
            throw new RuntimeException("Could not add listener to state machine " + statefulObject);
        }
    }

    protected static class StateListener<T>
    {
        protected final ObjectPool<StateListener<T>> pool;
        protected int interestSet;

        protected IntObjectBiConsumer<T> callback;

        protected PooledFuture<? super T> future;
        protected int successSet;

        public StateListener()
        {
            this(null);
        }

        public StateListener(ObjectPool<StateListener<T>> pool)
        {
            this.pool = pool;
        }

        public void release()
        {
            interestSet = 0;
            callback = null;
            future = null;
            successSet = 0;

            if (pool != null)
            {
                pool.returnObject(this);
            }
        }

        public void invoke(int newState, T obj)
        {
            if (callback != null)
            {
                callback.accept(newState, obj);
            }
            else
            {
                if ((newState & successSet) != 0)
                {
                    future.resolve(obj);
                }
                else
                {
                    future.fail();
                }
            }
        }
    }

}
