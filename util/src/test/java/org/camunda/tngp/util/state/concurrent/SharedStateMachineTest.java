package org.camunda.tngp.util.state.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.camunda.tngp.util.IntObjectBiConsumer;
import org.camunda.tngp.util.state.concurrent.SharedStateMachine.StateListener;
import org.junit.Before;
import org.junit.Test;

public class SharedStateMachineTest
{

    protected static final int STATE_A = 1 << 0;
    protected static final int STATE_B = 1 << 1;
    protected static final int STATE_C = 1 << 2;
    protected static final int ALL_STATES = STATE_A | STATE_B | STATE_C;

    private SharedStateMachineManager<Object> manager;

    protected static final StateListener<Object>[] NO_LISTENERS = new StateListener[0];

    @Before
    public void setUp()
    {
        final ManyToOneRingBuffer transitionBuffer = new ManyToOneRingBuffer(new UnsafeBuffer(new byte[RingBufferDescriptor.TRAILER_LENGTH + 1024]));
        manager = new SharedStateMachineManager<>(transitionBuffer);
    }

    @Test
    public void shouldMakeStateChange()
    {
        // given
        final SharedStateMachine<Object> stateMachine = new SharedStateMachine<>(
                1, new Object(), 10, NO_LISTENERS, manager);
        manager.register(stateMachine);

        // when
        final boolean success = stateMachine.makeStateTransition(STATE_A);

        // then
        assertThat(success).isTrue();
        assertThat(stateMachine.isInState(STATE_A)).isTrue();
        assertThat(stateMachine.isInState(STATE_B)).isFalse();
        assertThat(stateMachine.isInState(STATE_C)).isFalse();
        assertThat(stateMachine.isInAnyState(STATE_A | STATE_B | STATE_C)).isTrue();
    }

    @Test
    public void shouldRejectStateChangeIfPreconditionNotFulfilled()
    {
        // given
        final SharedStateMachine<Object> stateMachine = new SharedStateMachine<>(
                1, new Object(), 10, NO_LISTENERS, manager);
        manager.register(stateMachine);
        stateMachine.makeStateTransition(STATE_A);

        // when
        final boolean success = stateMachine.makeStateTransition(STATE_B, STATE_A);

        // then
        assertThat(success).isFalse();
        assertThat(stateMachine.isInState(STATE_B)).isFalse();
        assertThat(stateMachine.isInState(STATE_A)).isTrue();
    }

    @Test
    public void shouldDispatchStateChangeToDynamicListener()
    {
        // given
        final SharedStateMachine<Object> stateMachine = new SharedStateMachine<>(
                1, new Object(), 10, NO_LISTENERS, manager);
        manager.register(stateMachine);

        final List<Integer> stateChanges = new ArrayList<>();

        stateMachine.listenFor(STATE_A, (s, o) -> stateChanges.add(s));
        stateMachine.makeStateTransition(STATE_A);

        // when
        final int eventsDispatched = manager.dispatchTransitionEvents();

        // then
        assertThat(eventsDispatched).isEqualTo(1);
        assertThat(stateChanges).containsExactly(STATE_A);
    }

    @Test
    public void shouldNotInvokeListenerForUnwantedStateChange()
    {
        // given
        final SharedStateMachine<Object> stateMachine = new SharedStateMachine<>(
                1, new Object(), 10, NO_LISTENERS, manager);
        manager.register(stateMachine);

        final List<Integer> stateChanges = new ArrayList<>();

        stateMachine.listenFor(STATE_A, (s, o) -> stateChanges.add(s));
        stateMachine.makeStateTransition(STATE_B);

        // when
        final int eventsDispatched = manager.dispatchTransitionEvents();

        // then
        assertThat(eventsDispatched).isEqualTo(1);
        assertThat(stateChanges).isEmpty();
    }

    @Test
    public void shouldDeregisterDynamicListenerAfterInvocation()
    {
        // given
        final SharedStateMachine<Object> stateMachine = new SharedStateMachine<>(
                1, new Object(), 10, NO_LISTENERS, manager);
        manager.register(stateMachine);

        final List<Integer> stateChanges = new ArrayList<>();

        stateMachine.listenFor(ALL_STATES, (s, o) -> stateChanges.add(s));
        stateMachine.makeStateTransition(STATE_A);
        stateMachine.makeStateTransition(STATE_B);

        // when
        final int eventsDispatched = manager.dispatchTransitionEvents();

        // then the listener is not invoked for the second transition
        assertThat(eventsDispatched).isEqualTo(2);
        assertThat(stateChanges).containsExactly(STATE_A);
    }

    @Test
    public void shouldDispatchMultipleStateChangesForSameStateMachine()
    {
        // given
        final List<Integer> stateChanges = new ArrayList<>();

        final SharedStateMachine<Object> stateMachine = new SharedStateMachine<>(
                1, new Object(), 10, listener(ALL_STATES, (s, o) -> stateChanges.add(s)), manager);
        manager.register(stateMachine);

        stateMachine.makeStateTransition(STATE_A);
        stateMachine.makeStateTransition(STATE_A, STATE_B);
        stateMachine.makeStateTransition(STATE_B, STATE_C);
        stateMachine.makeStateTransition(STATE_A, STATE_C);

        // when
        final int eventsDispatched = manager.dispatchTransitionEvents();

        // then
        assertThat(eventsDispatched).isEqualTo(3);
        assertThat(stateChanges).containsExactly(STATE_A, STATE_B, STATE_C);
    }

    @Test
    public void shouldDispatchStateChangesForMultipleStateMachines()
    {
        // given
        final List<Integer> stateChanges1 = new ArrayList<>();
        final List<Integer> stateChanges2 = new ArrayList<>();

        final SharedStateMachine<Object> stateMachine1 = new SharedStateMachine<>(
                1, new Object(), 10, listener(ALL_STATES, (s, o) -> stateChanges1.add(s)), manager);
        manager.register(stateMachine1);
        final SharedStateMachine<Object> stateMachine2 = new SharedStateMachine<>(
                2, new Object(), 10, listener(ALL_STATES, (s, o) -> stateChanges2.add(s)), manager);
        manager.register(stateMachine2);

        stateMachine1.makeStateTransition(STATE_A);
        stateMachine2.makeStateTransition(STATE_B);
        stateMachine1.makeStateTransition(STATE_C);
        stateMachine2.makeStateTransition(STATE_A);

        // when
        final int eventsDispatched = manager.dispatchTransitionEvents();

        // then
        assertThat(eventsDispatched).isEqualTo(4);
        assertThat(stateChanges1).containsExactly(STATE_A, STATE_C);
        assertThat(stateChanges2).containsExactly(STATE_B, STATE_A);
    }

    /**
     * Case for deadlock potential
     */
    @Test
    public void shouldMakeStateChangeAndDispatchEventsInParallel()
    {
        // given
        final SharedStateMachine<Object> stateMachine = new SharedStateMachine<>(
                1, new Object(), 10, NO_LISTENERS, manager);
        manager.register(stateMachine);
        stateMachine.makeStateTransition(STATE_A);

        final ControllableAction a1 = new ControllableAction(c ->
        {
            stateMachine.listenFor(STATE_A, (s, o) ->
            {
                c.signalAndWait();
                stateMachine.makeStateTransition(STATE_B);
            });

            c.signalAndWait();
            manager.dispatchTransitionEvents();
        });

        final ControllableAction a2 = new ControllableAction(c ->
        {
            c.signalAndWait();
            stateMachine.makeStateTransitionAndDo(ALL_STATES, STATE_C, s ->
            {
                c.signalAndWait();
                s.listenFor(STATE_C, (s2, o) ->
                {
                });
            });
        });

        // when
        final ThreadControl t1 = a1.run();
        final ThreadControl t2 = a2.run();

        t1.waitForSignal(); // wait before dispatching events
        t2.waitForSignal(); // wait before making transition to C

        t1.signal(); // dispatch event A and wait in listener
        t1.waitForSignal();

        t2.signal(); // attempt making transition to C
        t1.signal(); // continue with listener, make transition to B

        t2.waitForSignal(); // wait until transition to C is in progress
        t2.signal(); // continue transition to C

        // then
        assertThat(stateMachine.isInState(STATE_C)).isTrue();
    }

    protected static StateListener<Object>[] listener(int interestSet, IntObjectBiConsumer<Object> listener)
    {
        final StateListener<Object> result = new StateListener<>();
        result.interestSet = interestSet;
        result.callback = listener;
        return new StateListener[]{ result };
    }

    protected static class ControllableAction
    {
        protected Object monitor = new Object();
        protected Consumer<ThreadControl> action;

        public ControllableAction(Consumer<ThreadControl> action)
        {
            this.action = action;
        }

        public ThreadControl run()
        {
            final ThreadControl threadControl = new ThreadControl();
            final Thread t = new Thread(() -> action.accept(threadControl));

            t.start();

            return threadControl;
        }
    }

    protected static class ThreadControl
    {
        protected Object monitor = new Object();
        protected Object signalIssuer = null;

        public void signalAndWait()
        {
            synchronized (monitor)
            {
                signal();
                waitForSignal();
            }
        }

        public void signal()
        {
            synchronized (monitor)
            {
                monitor.notify();
                this.signalIssuer = Thread.currentThread();
            }
        }

        public void waitForSignal()
        {
            synchronized (monitor)
            {
                if (!externalSignalAvailable())
                {
                    try
                    {
                        monitor.wait();
                    }
                    catch (InterruptedException e)
                    {
                        throw new RuntimeException(e);
                    }
                }

                signalIssuer = null;
            }
        }

        protected boolean externalSignalAvailable()
        {
            return signalIssuer != null && signalIssuer != Thread.currentThread();
        }
    }
}
