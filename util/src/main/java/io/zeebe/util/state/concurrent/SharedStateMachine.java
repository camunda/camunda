package io.zeebe.util.state.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.agrona.BitUtil;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.util.IntObjectBiConsumer;
import io.zeebe.util.PooledFuture;
import io.zeebe.util.collection.ObjectPool;

public class SharedStateMachine<T>
{

    protected final int id;
    protected final T statefulObject;
    protected final StateListener<T>[] staticListeners;
    protected final StateListener<T>[] dynamicListeners;

    protected final ObjectPool<StateListener<T>> pool;

    // state management
    protected volatile int state;

    protected final AtomicReference<Thread> mutexHolder = new AtomicReference<>(null);

    protected final SharedStateMachineManager<T> stateManager;
    protected final UnsafeBuffer writeBuf = new UnsafeBuffer(new byte[BitUtil.SIZE_OF_INT]);

    public SharedStateMachine(
            int id,
            T statefulObject,
            int listenerCapacity,
            StateListener<T>[] staticListeners,
            SharedStateMachineManager<T> stateManager)
    {
        this.id = id;
        this.staticListeners = staticListeners;
        this.statefulObject = statefulObject;
        this.dynamicListeners = new StateListener[listenerCapacity];
        this.pool = new ObjectPool<>(listenerCapacity, StateListener::new);
        this.stateManager = stateManager;
    }

    public void listenFor(int interestSet, IntObjectBiConsumer<T> listener)
    {
        final StateListener<T> stateListener = this.pool.request();
        stateListener.interestSet = interestSet;
        stateListener.callback = listener;

        registerListenerOrReturnToPool(stateListener);
    }

    public void listenFor(int expectedStateMask, int exceptionalState, CompletableFuture<? super T> future)
    {
        final StateListener<T> stateListener = this.pool.request();

        stateListener.interestSet = expectedStateMask;
        if (exceptionalState > 0)
        {
            stateListener.interestSet |= exceptionalState;
        }
        stateListener.successSet = expectedStateMask;
        stateListener.completableFuture = future;

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
        final boolean mustRelease = acquireMutex();

        try
        {
            dispatchEventToListeners(staticListeners, newState, false);
            dispatchEventToListeners(dynamicListeners, newState, true);
        }
        finally
        {
            if (mustRelease)
            {
                releaseMutex();
            }
        }
    }

    /**
     * @return true if the mutex has been newly acquired or false if it was already acquired
     */
    protected boolean acquireMutex()
    {
        final Thread requestingHolder = Thread.currentThread();

        // if we have the mutex already, noone else can release it, so comparing
        // this in the following lines should be thread-safe
        final Thread currentHolder = mutexHolder.get();

        if (currentHolder == requestingHolder)
        {
            return false;
        }
        else
        {
            while (!mutexHolder.compareAndSet(null, requestingHolder))
            {
            }
            return true;
        }
    }

    protected void releaseMutex()
    {
        mutexHolder.set(null);
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
                    stateListener.release();
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
        return makeStateTransitionAndDo(expectedStateMask, newState, null);
    }

    public boolean makeStateTransitionAndDo(int expectedStateMask, int newState, Consumer<SharedStateMachine<T>> actionOnSuccess)
    {

        final boolean mustRelease = acquireMutex();

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
                writeBuf.putInt(0, newState);
                final boolean stateChangeRecorded = stateManager.recordStateChange(id, writeBuf);
                if (!stateChangeRecorded)
                {
                    throw new RuntimeException("Could not record state change for " + statefulObject);
                }

                state = newState;

                if (actionOnSuccess != null)
                {
                    actionOnSuccess.accept(this);
                }

            }

            return canEnterState;
        }
        finally
        {
            if (mustRelease)
            {
                releaseMutex();
            }
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
        final boolean mustRelease = acquireMutex();

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
            if (mustRelease)
            {
                releaseMutex();
            }
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
        protected CompletableFuture<? super T> completableFuture;
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
            completableFuture = null;
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
            else if (future != null)
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
            else
            {
                if ((newState & successSet) != 0)
                {
                    completableFuture.complete(obj);
                }
                else
                {
                    completableFuture.cancel(true);
                }
            }
        }
    }

}
