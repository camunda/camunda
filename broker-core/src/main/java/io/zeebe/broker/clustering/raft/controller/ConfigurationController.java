package io.zeebe.broker.clustering.raft.controller;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import io.zeebe.broker.clustering.raft.Configuration;
import io.zeebe.broker.clustering.raft.Member;
import io.zeebe.broker.clustering.raft.Raft;
import io.zeebe.broker.clustering.raft.RaftContext;
import io.zeebe.broker.clustering.raft.entry.ConfigurationEntry;
import io.zeebe.broker.clustering.raft.entry.ConfiguredMember;
import io.zeebe.broker.util.msgpack.value.ArrayValueIterator;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.StateMachineCommand;
import io.zeebe.util.state.TransitionState;
import io.zeebe.util.state.WaitState;

public class ConfigurationController
{
    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_OPEN = 1;
    protected static final int TRANSITION_CLOSE = 2;
    protected static final int TRANSITION_FAILED = 3;

    private static final StateMachineCommand<ConfigurationContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        c.reset();

        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    protected final WaitState<ConfigurationContext> closedState = (c) ->
    {
    };
    protected final WaitState<ConfigurationContext> configuredState = (c) ->
    {
    };
    protected final WaitState<ConfigurationContext> failedState = (c) ->
    {
    };

    private final PrepareConfigurationEntryState prepareConfigurationState = new PrepareConfigurationEntryState();
    private final OpenAppendControllerState openAppendControllerState = new OpenAppendControllerState();
    private final AppendConfigurationState appendConfigurationState = new AppendConfigurationState();
    private final ApplyConfigurationState applyConfigurationState = new ApplyConfigurationState();
    private final CommitConfigurationState commitConfigurationState = new CommitConfigurationState();
    private final CloseAppendControllerState closeAppendControllerState = new CloseAppendControllerState();
    private final ClosingState closingState = new ClosingState();

    private final StateMachineAgent<ConfigurationContext> configurationStateMachine;
    private ConfigurationContext configurationContext;


    public ConfigurationController(final RaftContext raftContext)
    {
        this.configurationStateMachine = new StateMachineAgent<>(StateMachine
                .<ConfigurationContext> builder(s ->
                {
                    configurationContext = new ConfigurationContext(s, raftContext);
                    return configurationContext;
                })
                .initialState(closedState)

                .from(closedState).take(TRANSITION_OPEN).to(prepareConfigurationState)
                .from(closedState).take(TRANSITION_CLOSE).to(closedState)

                .from(prepareConfigurationState).take(TRANSITION_DEFAULT).to(openAppendControllerState)
                .from(prepareConfigurationState).take(TRANSITION_CLOSE).to(closeAppendControllerState)

                .from(openAppendControllerState).take(TRANSITION_DEFAULT).to(appendConfigurationState)
                .from(openAppendControllerState).take(TRANSITION_FAILED).to(failedState)
                .from(openAppendControllerState).take(TRANSITION_CLOSE).to(closeAppendControllerState)

                .from(appendConfigurationState).take(TRANSITION_DEFAULT).to(applyConfigurationState)
                .from(appendConfigurationState).take(TRANSITION_FAILED).to(failedState)
                .from(appendConfigurationState).take(TRANSITION_CLOSE).to(closeAppendControllerState)

                .from(applyConfigurationState).take(TRANSITION_DEFAULT).to(commitConfigurationState)
                .from(applyConfigurationState).take(TRANSITION_FAILED).to(failedState)
                .from(applyConfigurationState).take(TRANSITION_CLOSE).to(closeAppendControllerState)

                .from(commitConfigurationState).take(TRANSITION_DEFAULT).to(configuredState)
                .from(commitConfigurationState).take(TRANSITION_FAILED).to(failedState)
                .from(commitConfigurationState).take(TRANSITION_CLOSE).to(closeAppendControllerState)

                .from(configuredState).take(TRANSITION_CLOSE).to(closeAppendControllerState)
                .from(failedState).take(TRANSITION_CLOSE).to(closeAppendControllerState)

                .from(closeAppendControllerState).take(TRANSITION_DEFAULT).to(closingState)
                .from(closeAppendControllerState).take(TRANSITION_CLOSE).to(closeAppendControllerState)

                .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
                .from(closingState).take(TRANSITION_CLOSE).to(closingState)

                .build());
    }

    public void open(final List<Member> members)
    {
        if (isClosed())
        {
            configurationContext.members.clear();
            configurationContext.members.addAll(members);
            configurationContext.take(TRANSITION_OPEN);
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }
    }

    public CompletableFuture<Void> openAsync(final List<Member> members)
    {
        if (isClosed())
        {
            final CompletableFuture<Void> future = new CompletableFuture<>();

            configurationContext.members.clear();
            configurationContext.members.addAll(members);
            configurationContext.configurationFuture = future;

            configurationContext.take(TRANSITION_OPEN);

            return future;
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }
    }

    public void close()
    {
        configurationStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
    }

    public int doWork()
    {
        return configurationStateMachine.doWork();
    }

    public boolean isClosed()
    {
        return configurationStateMachine.getCurrentState() == closedState;
    }

    public boolean isFailed()
    {
        return configurationStateMachine.getCurrentState() == failedState;
    }

    public boolean isAppended()
    {
        return configurationContext.appendController.isAppended();
    }

    public boolean isConfigured()
    {
        return configurationStateMachine.getCurrentState() == configuredState;
    }

    static class ConfigurationContext extends SimpleStateMachineContext
    {
        final RaftContext raftContext;
        final AppendController appendController;
        final ConfigurationEntry configuration;
        final List<Member> members;

        CompletableFuture<Void> configurationFuture;

        ConfigurationContext(final StateMachine<?> stateMachine, final RaftContext raftContext)
        {
            super(stateMachine);
            this.raftContext = raftContext;
            this.appendController = new AppendController(raftContext);
            this.configuration = new ConfigurationEntry();
            this.members = new CopyOnWriteArrayList<>();
        }

        public void reset()
        {
            configuration.reset();
        }
    }

    static class PrepareConfigurationEntryState implements TransitionState<ConfigurationContext>
    {
        @Override
        public void work(ConfigurationContext context) throws Exception
        {
            final ConfigurationEntry configuration = context.configuration;
            final List<Member> members = context.members;

            configuration.reset();
            final ArrayValueIterator<ConfiguredMember> iterator = configuration.members();
            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                final SocketAddress endpoint = member.endpoint();
                iterator.add()
                    .setPort(endpoint.port())
                    .setHost(endpoint.getHostBuffer(), 0, endpoint.hostLength());
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class OpenAppendControllerState implements TransitionState<ConfigurationContext>
    {
        @Override
        public void work(ConfigurationContext context) throws Exception
        {
            final ConfigurationEntry configuration = context.configuration;
            final AppendController appendController = context.appendController;

            appendController.open(configuration, true);

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class AppendConfigurationState implements State<ConfigurationContext>
    {
        @Override
        public int doWork(ConfigurationContext context) throws Exception
        {
            final AppendController appendController = context.appendController;

            int workcount = 0;

            workcount += appendController.doWork();

            if (appendController.isAppended())
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }
            else if (appendController.isFailed())
            {
                final CompletableFuture<Void> future = context.configurationFuture;
                if (future != null)
                {
                    future.completeExceptionally(new RuntimeException("Appending failed!"));
                }

                context.take(TRANSITION_FAILED);
            }

            return workcount;
        }
    }

    static class ApplyConfigurationState implements TransitionState<ConfigurationContext>
    {
        @Override
        public void work(ConfigurationContext context) throws Exception
        {
            final RaftContext raftContext = context.raftContext;
            final Raft raft = raftContext.getRaft();
            final AppendController appendController = context.appendController;
            final List<Member> members = context.members;

            final long position = appendController.entryPosition();

            final List<Member> copy = new CopyOnWriteArrayList<>(members);
            raft.configure(new Configuration(position, raft.term(), copy));

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class CommitConfigurationState implements State<ConfigurationContext>
    {
        @Override
        public int doWork(ConfigurationContext context) throws Exception
        {
            final AppendController appendController = context.appendController;

            int workcount = 0;

            workcount += appendController.doWork();

            if (appendController.isCommitted())
            {
                final CompletableFuture<Void> future = context.configurationFuture;
                if (future != null)
                {
                    future.complete(null);
                }

                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    static class CloseAppendControllerState implements TransitionState<ConfigurationContext>
    {
        @Override
        public void work(ConfigurationContext context) throws Exception
        {
            final AppendController appendController = context.appendController;

            if (!appendController.isClosed())
            {
                appendController.close();
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class ClosingState implements State<ConfigurationContext>
    {
        @Override
        public int doWork(ConfigurationContext context) throws Exception
        {
            final AppendController appendController = context.appendController;

            int workcount = 0;

            workcount += appendController.doWork();

            if (appendController.isClosed())
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }
}
