package io.zeebe.broker.clustering.raft.controller;

import static io.zeebe.protocol.clientapi.EventType.RAFT_EVENT;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.raft.Raft;
import io.zeebe.broker.clustering.raft.RaftContext;
import io.zeebe.broker.logstreams.BrokerEventMetadata;
import io.zeebe.logstreams.impl.LogStreamController;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamFailureListener;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.StateMachineCommand;
import io.zeebe.util.state.TransitionState;
import io.zeebe.util.state.WaitState;
import org.slf4j.Logger;

public class AppendController
{
    public static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_CLOSE = 2;
    private static final int TRANSITION_FAIL = 3;
    private static final int TRANSITION_APPENDED = 4;

    private static final StateMachineCommand<AppendContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        c.reset();

        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    private static final StateMachineCommand<AppendContext> FAILED_STATE_MACHINE_COMMAND = (c) ->
    {
        final boolean failed = c.tryTake(TRANSITION_FAIL);
        if (!failed)
        {
            throw new IllegalStateException("Cannot transition to failed state.");
        }
    };

    private WaitState<AppendContext> closedState = (c) ->
    {
    };
    private WaitState<AppendContext> appendedState = (c) ->
    {
    };
    private WaitState<AppendContext> committedState = (c) ->
    {
    };
    private WaitState<AppendContext> failedState = (c) ->
    {
    };

    protected final WriteState writeState = new WriteState();
    protected final AppendState appendState = new AppendState();
    protected final CommitState commitState = new CommitState();
    protected final ClosingState closingState = new ClosingState();

    protected final StateMachineAgent<AppendContext> appendStateMachine;
    protected AppendContext appendContext;

    public AppendController(final RaftContext raftContext)
    {
        this.appendStateMachine = new StateMachineAgent<>(StateMachine
                .<AppendContext> builder(s ->
                {
                    appendContext = new AppendContext(s, raftContext);
                    return appendContext;
                })

                .initialState(closedState)

                .from(closedState).take(TRANSITION_OPEN).to(writeState)
                .from(closedState).take(TRANSITION_CLOSE).to(writeState)

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
                .from(closingState).take(TRANSITION_CLOSE).to(closingState)

                .build());
    }

    public void open(final BufferWriter writer, final boolean waitForCommit)
    {
        if (isClosed())
        {
            appendContext.entry = writer;
            appendContext.waitForCommit = waitForCommit;
            appendContext.take(TRANSITION_OPEN);
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }
    }

    public void close()
    {
        appendStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
    }

    public int doWork()
    {
        return appendStateMachine.doWork();
    }

    public long entryPosition()
    {
        if (isAppended())
        {
            return appendContext.entryPosition;
        }

        return -1L;
    }

    public boolean isAppended()
    {
        return appendStateMachine.getCurrentState() == appendedState ||
                appendStateMachine.getCurrentState() == commitState || isCommitted();
    }

    public boolean isCommitted()
    {
        return appendStateMachine.getCurrentState() == committedState;
    }

    public boolean isFailed()
    {
        return appendStateMachine.getCurrentState() == failedState;
    }

    public boolean isClosed()
    {
        return appendStateMachine.getCurrentState() == closedState;
    }

    class AppendContext extends SimpleStateMachineContext
    {
        final Raft raft;
        final LogStreamWriter logStreamWriter;
        final BrokerEventMetadata metadata;
        final LogStreamListener listener;

        BufferWriter entry;
        long entryPosition = -1L;
        boolean waitForCommit = false;

        AppendContext(StateMachine<?> stateMachine, final RaftContext raftContext)
        {
            super(stateMachine);
            this.metadata =  new BrokerEventMetadata();
            this.raft = raftContext.getRaft();
            this.logStreamWriter = new LogStreamWriterImpl();
            this.listener = new LogStreamListener();
        }

        public void reset()
        {
            entry = null;
            entryPosition = -1L;
            waitForCommit = false;
        }
    }

    static class WriteState implements State<AppendContext>
    {
        @Override
        public int doWork(AppendContext context) throws Exception
        {
            final Raft raft = context.raft;
            final BrokerEventMetadata metadata = context.metadata;
            final LogStreamWriter logStreamWriter = context.logStreamWriter;
            final BufferWriter entry = context.entry;
            final LogStreamListener listener = context.listener;

            final LogStream stream = raft.stream();
            final LogStreamController logStreamController = stream.getLogStreamController();

            int workcount = 0;

            logStreamController.registerFailureListener(listener);

            metadata.reset();
            metadata.raftTermId(raft.term());
            metadata.eventType(RAFT_EVENT);

            logStreamWriter.wrap(stream);
            final long position = logStreamWriter
                    .positionAsKey()
                    .valueWriter(entry)
                    .metadataWriter(metadata)
                    .tryWrite();

            if (position >= 0)
            {
                workcount += 1;
                context.entryPosition = position;
                context.take(TRANSITION_DEFAULT);
            }
            else
            {
                logStreamController.removeFailureListener(listener);
            }

            return workcount;
        }

        @Override
        public void onFailure(AppendContext context, Exception e)
        {
            final Raft raft = context.raft;
            final LogStream stream = raft.stream();
            final LogStreamController logStreamController = stream.getLogStreamController();
            final LogStreamListener listener = context.listener;

            if (logStreamController != null)
            {
                logStreamController.removeFailureListener(listener);
            }

            LOG.error("Failed to write state", e);

            context.take(TRANSITION_FAIL);
        }
    }

    static class AppendState implements State<AppendContext>
    {
        @Override
        public int doWork(AppendContext context) throws Exception
        {
            final Raft raft = context.raft;
            final LogStream stream = raft.stream();

            final long entryPosition = context.entryPosition;
            final boolean waitForCommit = context.waitForCommit;

            int workcount = 0;

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

    static class CommitState implements State<AppendContext>
    {
        @Override
        public int doWork(AppendContext context) throws Exception
        {
            final Raft raft = context.raft;

            final long entryPosition = context.entryPosition;

            int workcount = 0;

            if (raft.commitPosition() >= entryPosition)
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    static class ClosingState implements TransitionState<AppendContext>
    {
        @Override
        public void work(AppendContext context) throws Exception
        {
            final Raft raft = context.raft;
            final LogStream stream = raft.stream();
            final LogStreamController logStreamController = stream.getLogStreamController();
            final LogStreamListener listener = context.listener;

            if (logStreamController != null)
            {
                logStreamController.removeFailureListener(listener);
            }

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
            final long entryPosition = appendContext.entryPosition;

            if (failedPosition <= entryPosition)
            {
                appendStateMachine.addCommand(FAILED_STATE_MACHINE_COMMAND);
            }
        }
    }
}
