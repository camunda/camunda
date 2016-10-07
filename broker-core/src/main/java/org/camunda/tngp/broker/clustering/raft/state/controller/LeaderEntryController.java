package org.camunda.tngp.broker.clustering.raft.state.controller;

import static org.camunda.tngp.protocol.clientapi.EventType.*;

import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamFailureListener;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.StateMachineContext;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

public class LeaderEntryController
{
    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_OPEN = 1;
    protected static final int TRANSITION_CLOSE = 2;
    protected static final int TRANSITION_FAIL = 3;
    protected static final int TRANSITION_APPENDED = 4;

    protected final ClosedState closedState = new ClosedState();
    protected final FailedState failedState = new FailedState();
    protected final ClosingState closingState = new ClosingState();
    protected final WriteState writeState = new WriteState();
    protected final AppendState appendState = new AppendState();
    protected final AppendedState appendedState = new AppendedState();
    protected final CommitState commitState = new CommitState();
    protected final CommittedState committedState = new CommittedState();

    protected StateMachineAgent<StateMachineContext> entryStateMachine = new StateMachineAgent<>(StateMachine
            .<StateMachineContext> builder(s -> new SimpleStateMachineContext(s)).initialState(closedState)

            .from(closedState).take(TRANSITION_OPEN).to(writeState)

            .from(writeState).take(TRANSITION_DEFAULT).to(appendState)
            .from(writeState).take(TRANSITION_CLOSE).to(closingState)
            .from(writeState).take(TRANSITION_FAIL).to(failedState)

            .from(appendState).take(TRANSITION_DEFAULT).to(commitState)
            .from(appendState).take(TRANSITION_APPENDED).to(appendedState)
            .from(appendState).take(TRANSITION_CLOSE).to(closingState)
            .from(appendState).take(TRANSITION_FAIL).to(failedState)

            .from(commitState).take(TRANSITION_DEFAULT).to(committedState)
            .from(commitState).take(TRANSITION_CLOSE).to(closingState)
            .from(commitState).take(TRANSITION_FAIL).to(failedState)

            .from(appendedState).take(TRANSITION_CLOSE).to(closingState)
            .from(committedState).take(TRANSITION_CLOSE).to(closingState)
            .from(failedState).take(TRANSITION_CLOSE).to(closingState)

            .from(closingState).take(TRANSITION_DEFAULT).to(closedState)

            .build());

    protected final BrokerEventMetadata metadata = new BrokerEventMetadata();

    protected final LogStreamWriter logStreamWriter;

    protected final Raft raft;

    protected BufferWriter writer;
    protected boolean waitForCommit = false;;

    protected long entryPosition;

    public LeaderEntryController(final Raft raft)
    {
        this.raft = raft;
        this.logStreamWriter = new LogStreamWriter(raft.stream()).positionAsKey();
    }

    public void open(final BufferWriter writer, final boolean waitForCommit)
    {
        this.writer = writer;
        this.waitForCommit = waitForCommit;

        entryStateMachine.addCommand((c) ->
        {
            final boolean open = c.tryTake(TRANSITION_OPEN);
            if (!open)
            {
                throw new IllegalStateException("Cannot open state machine. State is not closed.");
            }
        });
    }

    public void close()
    {
        entryStateMachine.addCommand((c) ->
        {
            final boolean closed = c.tryTake(TRANSITION_CLOSE);
            if (!closed)
            {
                throw new IllegalStateException("Cannot close state machine.");
            }

            writer = null;
            waitForCommit = false;
        });
    }

    public int doWork()
    {
        return entryStateMachine.doWork();
    }

    public long entryPosition()
    {
        return entryPosition;
    }

    public boolean isEntryAppended()
    {
        return entryStateMachine.getCurrentState() == appendedState || entryStateMachine.getCurrentState() == commitState || isEntryCommitted();
    }

    public boolean isEntryCommitted()
    {
        return entryStateMachine.getCurrentState() == committedState;
    }

    public boolean isEntryFailed()
    {
        return entryStateMachine.getCurrentState() == failedState;
    }

    class ClosedState implements WaitState<StateMachineContext>
    {
        @Override
        public void work(final StateMachineContext context)
        {
            // nothing to do
        }
    }

    class FailedState implements WaitState<StateMachineContext>
    {
        @Override
        public void work(final StateMachineContext context)
        {
            // nothing to do
        }
    }

    class WriteState implements State<StateMachineContext>
    {
        @Override
        public int doWork(StateMachineContext context) throws Exception
        {
            int workcount = 0;

            metadata.reset();
            metadata.raftTermId(raft.term());
            metadata.eventType(RAFT_EVENT);

            final long position = logStreamWriter
                    .positionAsKey()
                    .valueWriter(writer)
                    .metadataWriter(metadata)
                    .tryWrite();

            if (position >= 0)
            {
                workcount += 1;

                entryPosition = position;
                context.take(TRANSITION_DEFAULT);
            }
            else
            {
                workcount += 1;

                context.take(TRANSITION_FAIL);
            }

            return workcount;
        }
    }

    class AppendState implements State<StateMachineContext>
    {
        @Override
        public int doWork(StateMachineContext context) throws Exception
        {
            int workcount = 0;

            final LogStream stream = raft.stream();

            if (stream.getCurrentAppenderPosition() >= entryPosition)
            {
                workcount += 1;

                if (waitForCommit)
                {
                    context.take(TRANSITION_DEFAULT);
                }
                else
                {
                    context.take(TRANSITION_APPENDED);
                }
            }

            return workcount;
        }
    }

    class AppendedState implements WaitState<StateMachineContext>
    {
        @Override
        public void work(StateMachineContext context) throws Exception
        {
            // do nothing;
        }
    }

    class CommitState implements State<StateMachineContext>
    {
        @Override
        public int doWork(StateMachineContext context) throws Exception
        {
            int workcount = 0;

//            if (raft.commitPosition() >= entryPosition)
            final LogStream stream = raft.stream();

            if (stream.getCurrentAppenderPosition() >= entryPosition)
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    class CommittedState implements WaitState<StateMachineContext>
    {
        @Override
        public void work(StateMachineContext context) throws Exception
        {
            // do nothing;
        }
    }

    class ClosingState implements TransitionState<StateMachineContext>
    {
        @Override
        public void work(StateMachineContext context) throws Exception
        {
            entryPosition = -1L;
            context.take(TRANSITION_DEFAULT);
        }
    }

    class LogStreamListener implements LogStreamFailureListener
    {
        @Override
        public void onRecovered()
        {
        }

        @Override
        public void onFailed(long failedPosition)
        {
            if (failedPosition <= entryPosition)
            {
                entryStateMachine.addCommand((c) ->
                {
                    final boolean closed = c.tryTake(TRANSITION_FAIL);
                    if (!closed)
                    {
                        throw new IllegalStateException("Cannot close state machine.");
                    }
                });
            }
        }
    };
}
