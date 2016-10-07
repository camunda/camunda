package org.camunda.tngp.broker.clustering.raft.state;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.camunda.tngp.broker.clustering.raft.entry.ConfigurationEntry;
import org.camunda.tngp.broker.clustering.raft.entry.InitializeEntry;
import org.camunda.tngp.broker.clustering.raft.message.AppendRequest;
import org.camunda.tngp.broker.clustering.raft.message.AppendResponse;
import org.camunda.tngp.broker.clustering.raft.message.JoinRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.broker.clustering.raft.protocol.Configuration;
import org.camunda.tngp.broker.clustering.raft.protocol.Member;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft.State;
import org.camunda.tngp.broker.clustering.raft.state.controller.LeaderEntryController;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

public class LeaderState extends ActiveState
{
    protected static final int TRANSITION_OPEN = 0;
    protected static final int TRANSITION_DEFAULT = 1;
    protected static final int TRANSITION_CONFIGURE = 2;
    protected static final int TRANSITION_FAILED = 3;
    protected static final int TRANSITION_CLOSE = 3;

    protected final ClosedState closedState = new ClosedState();
    protected final PreInitializeState preInitializeState = new PreInitializeState();
    protected final InitializeState initializeState = new InitializeState();
    protected final PreConfigurationState preConfigurationState = new PreConfigurationState();
    protected final AppendConfigurationState appendConfigureState = new AppendConfigurationState();
    protected final ApplyConfigurationState applyConfigureState = new ApplyConfigurationState();
    protected final CommitConfigurationState commitConfigureState = new CommitConfigurationState();
    protected final SendConfigurationState sendConfigurationState = new SendConfigurationState();
    protected final OpenState openState = new OpenState();

    protected LeaderContext leaderContext;

    protected final Function<StateMachine<LeaderContext>, LeaderContext> contextFactory = (s) ->
    {
        leaderContext = new LeaderContext(s);
        return leaderContext;
    };

    protected final StateMachineAgent<LeaderContext> leaderStateMachine = new StateMachineAgent<>(StateMachine
            .<LeaderContext> builder(contextFactory).initialState(closedState)

            .from(closedState).take(TRANSITION_OPEN).to(preInitializeState)

            .from(preInitializeState).take(TRANSITION_DEFAULT).to(initializeState)

            .from(initializeState).take(TRANSITION_DEFAULT).to(preConfigurationState)

            .from(preConfigurationState).take(TRANSITION_DEFAULT).to(appendConfigureState)

            .from(appendConfigureState).take(TRANSITION_DEFAULT).to(applyConfigureState)
            .from(appendConfigureState).take(TRANSITION_FAILED).to(sendConfigurationState)

            .from(applyConfigureState).take(TRANSITION_DEFAULT).to(commitConfigureState)

            .from(commitConfigureState).take(TRANSITION_DEFAULT).to(sendConfigurationState)

            .from(sendConfigurationState).take(TRANSITION_DEFAULT).to(openState)

            .from(openState).take(TRANSITION_CONFIGURE).to(preConfigurationState)
            .from(openState).take(TRANSITION_CLOSE).to(closedState)

            .build());

    protected final BrokerEventMetadata metadata = new BrokerEventMetadata();

    protected final InitializeEntry initialEntry = new InitializeEntry();
    protected final ConfigurationEntry configurationEntry = new ConfigurationEntry();

    protected final LeaderEntryController entryController;

    protected long leaderInitialEntryPosition = 0L;
    protected boolean configuring = false;

    protected DeferredResponse deferedJoinResponse;

    public LeaderState(final Raft raft, final LogStreamState logStreamState)
    {
        super(raft, logStreamState);
        this.entryController = new LeaderEntryController(raft);
    }

    @Override
    public State state()
    {
        return Raft.State.LEADER;
    }

    @Override
    public void doOpen()
    {
        System.out.println("[LEADER] leader for " + raft.id());

        takeLeadership();

        startAppendEntries();

        leaderStateMachine.addCommand((c) ->
        {
            final boolean closed = c.tryTake(TRANSITION_OPEN);
            if (!closed)
            {
                throw new IllegalStateException("Cannot open state machine.");
            }
        });
    }

    @Override
    public void doClose()
    {
        stepdown();

        stopAppendEntries();

        leaderStateMachine.addCommand((c) ->
        {
            final boolean closed = c.tryTake(TRANSITION_CLOSE);
            if (!closed)
            {
                throw new IllegalStateException("Cannot close state machine.");
            }
        });

    }

    @Override
    public int doWork()
    {
        int workcount = 0;

        workcount += doAppendEntries();
        workcount += leaderStateMachine.doWork();

        return workcount;
    }

    protected void startAppendEntries()
    {
        final Member leader = raft.member();
        final List<Member> members = raft.members();

        for (int i = 0; i < members.size(); i++)
        {
            final Member member = members.get(i);
            if (member != null && member.equals(leader))
            {
                member.resetToLastEntry();
                member.sendAppendRequest();
            }
        }
    }

    protected int doAppendEntries()
    {
        int workcount = 0;

        if (isOpen())
        {
            final Member leader = raft.member();
            final List<Member> members = raft.members();

            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);

                if (member != null && !member.equals(leader))
                {
                    workcount += member.doAppend();

                    if (member.shouldAppend())
                    {
                        workcount += 1;
                        member.cancelAppendRequest();
                        member.sendAppendRequest();
                        workcount += member.doAppend();
                    }
                }
            }
        }

        return workcount;
    }

    protected void stopAppendEntries()
    {
        final Member leader = raft.member();
        final List<Member> members = raft.members();

        for (int i = 0; i < members.size(); i++)
        {
            final Member member = members.get(i);
            if (member != null && member.equals(leader))
            {
                member.cancelAppendRequest();
            }
        }
    }

    public boolean initializing()
    {
        return leaderInitialEntryPosition == 0;
    }

    public boolean configuring()
    {
        return configuring;
    }

    protected void takeLeadership()
    {
        final Member member = raft.member();
        final Endpoint endpoint = member.endpoint();

        raft.leader(endpoint);
    }

    protected void stepdown()
    {
        leaderInitialEntryPosition = 0;
        configuring = false;
    }

    @Override
    public void join(final JoinRequest request, final DeferredResponse response)
    {
        joinResponse.reset();


        if (configuring() || initializing())
        {
            joinResponse
                .term(raft.term())
                .log(raft.stream().getId())
                .status(false)
                .members(raft.members());

            if (response.allocateAndWrite(joinResponse))
            {
                response.commit();
            }
        }
        else
        {
            final Member joiningMember = request.member();
            if (!raft.members().contains(joiningMember))
            {
                final List<Member> members = new CopyOnWriteArrayList<>(raft.members());
                members.add(joiningMember);

                raft.configure(new Configuration(0, 0, members));

                leaderStateMachine.addCommand((c) -> c.tryTake(TRANSITION_CONFIGURE));

                response.defer();
                deferedJoinResponse = response;
            }
            else
            {
                joinResponse
                    .term(raft.term())
                    .log(raft.stream().getId())
                    .status(true)
                    .configurationEntryPosition(raft.configuration().configurationEntryPosition())
                    .configurationEntryTerm(raft.configuration().configurationEntryTerm())
                    .members(raft.members());

                if (response.allocateAndWrite(joinResponse))
                {
                    response.commit();
                }
            }
        }
    }

    @Override
    public VoteResponse vote(VoteRequest request)
    {
        if (updateTermAndLeader(request.term(), null))
        {
            raft.transition(Raft.State.FOLLOWER);
            return super.vote(request);
        }
        else
        {
            voteResponse.reset();
            return voteResponse.term(raft.term())
                .granted(false);
        }
    }

    @Override
    public AppendResponse append(final AppendRequest request)
    {
        AppendResponse response = null;

        if (updateTermAndLeader(request.term(), request.leader()))
        {
            raft.transition(Raft.State.FOLLOWER);
            response = super.append(request);
        }
        else if (request.term() < raft.term())
        {
            response = rejectAppendRequest(request.previousEntryPosition());
        }
        else
        {
            raft.transition(Raft.State.FOLLOWER);
            response = super.append(request);
        }

        return response;
    }

    class LeaderContext extends SimpleStateMachineContext
    {
        protected final List<Member> members = new CopyOnWriteArrayList<>();
        protected boolean configurationSucceeded = false;

        LeaderContext(final StateMachine<LeaderContext> stateMachine)
        {
            super(stateMachine);
        }
    }

    class PreInitializeState implements TransitionState<LeaderContext>
    {
        @Override
        public void work(LeaderContext context) throws Exception
        {
            initialEntry.reset();
            entryController.open(initialEntry, false);
            context.take(TRANSITION_DEFAULT);
        }
    }

    class InitializeState implements org.camunda.tngp.util.state.State<LeaderContext>
    {
        @Override
        public int doWork(LeaderContext context) throws Exception
        {
            int workcount = 0;

            workcount += entryController.doWork();

            if (entryController.isEntryAppended())
            {
                workcount += 1;
                leaderInitialEntryPosition = entryController.entryPosition();

                final Configuration configuration = raft.configuration();
                final List<Member> members = configuration.members();
                context.members.clear();
                context.members.addAll(members);

                context.take(TRANSITION_DEFAULT);
            }
            else if (entryController.isEntryFailed())
            {
                // failed to append initial entry, stepdown as leader
                raft.transition(State.FOLLOWER);
            }

            return workcount;
        }

        @Override
        public void onExit()
        {
            entryController.close();
            entryController.doWork();
        }
    }

    class PreConfigurationState implements TransitionState<LeaderContext>
    {
        @Override
        public void work(LeaderContext context) throws Exception
        {
            configuring = true;

            configurationEntry.reset();
            configurationEntry.members(context.members);
            entryController.open(configurationEntry, true);
            context.take(TRANSITION_DEFAULT);
        }
    }

    class AppendConfigurationState implements org.camunda.tngp.util.state.State<LeaderContext>
    {
        @Override
        public int doWork(LeaderContext context) throws Exception
        {
            int workcount = 0;

            workcount += entryController.doWork();

            if (entryController.isEntryAppended())
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }
            else if (entryController.isEntryFailed())
            {
                workcount += 1;
                context.configurationSucceeded = false;
                context.take(TRANSITION_FAILED);
            }

            return workcount;
        }

    }

    class ApplyConfigurationState implements TransitionState<LeaderContext>
    {
        @Override
        public void work(LeaderContext context) throws Exception
        {
            final long newConfigPosition = entryController.entryPosition();
            final List<Member> members = new CopyOnWriteArrayList<>(context.members);
            raft.configure(new Configuration(newConfigPosition, raft.term(), members));
            context.take(TRANSITION_DEFAULT);

            final BufferedLogStreamReader reader = new BufferedLogStreamReader();
            reader.wrap(raft.stream());
            reader.seek(newConfigPosition);
            if (reader.hasNext())
            {
                final LoggedEvent event = reader.next();
                configurationEntry.wrap(event.getValueBuffer(), event.getValueOffset(), event.getValueLength());
            }
        }
    }

    class CommitConfigurationState implements org.camunda.tngp.util.state.State<LeaderContext>
    {
        @Override
        public int doWork(LeaderContext context) throws Exception
        {
            int workcount = 0;

            workcount += entryController.doWork();

            if (entryController.isEntryCommitted())
            {
                workcount += 1;
                context.configurationSucceeded = true;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }

        @Override
        public void onExit()
        {
            entryController.close();
            entryController.doWork();
        }
    }

    class SendConfigurationState implements TransitionState<LeaderContext>
    {
        @Override
        public void work(LeaderContext context) throws Exception
        {
            if (deferedJoinResponse != null)
            {
                joinResponse.reset();
                joinResponse
                    .log(raft.stream().getId())
                    .term(raft.term())
                    .status(context.configurationSucceeded);

                if (context.configurationSucceeded)
                {
                    final Configuration configuration = raft.configuration();
                    joinResponse
                        .configurationEntryPosition(configuration.configurationEntryPosition())
                        .configurationEntryTerm(configuration.configurationEntryTerm())
                        .members(configuration.members());
                }

                if (deferedJoinResponse.allocateAndWrite(configureResponse))
                {
                    deferedJoinResponse.commit();
                }
                else
                {
                    deferedJoinResponse.abort();
                }
            }

            context.configurationSucceeded = false;
            context.take(TRANSITION_DEFAULT);
        }

        @Override
        public void onExit()
        {
            deferedJoinResponse = null;
            configuring = false;
        }
    }

    class OpenState implements WaitState<LeaderContext>
    {
        @Override
        public void work(LeaderContext context) throws Exception
        {
            // nothing to do!
        }
    }

    class ClosedState implements WaitState<LeaderContext>
    {
        @Override
        public void work(LeaderContext context) throws Exception
        {
            // nothing to do!
        }
    }
}
