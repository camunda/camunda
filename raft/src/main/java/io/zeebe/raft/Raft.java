/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.raft;

import static io.zeebe.util.EnsureUtil.ensureNotNull;

import java.time.Duration;
import java.util.*;

import io.zeebe.logstreams.impl.LogStreamController;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.raft.controller.*;
import io.zeebe.raft.event.RaftConfigurationEventMember;
import io.zeebe.raft.protocol.*;
import io.zeebe.raft.state.*;
import io.zeebe.transport.*;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.ScheduledTimer;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

/**
 * <p>
 * Representation of a member of a raft cluster. It handle three concerns of the
 * raft member:
 * </p>
 *
 * <ul>
 *     <li>holding and updating the raft state</li>
 *     <li>handling raft protocol messages</li>
 *     <li>advancing the work on raft concerns, i.e. replicating the log, triggering elections etc.</li>
 * </ul>
 *
 */
public class Raft extends Actor implements ServerMessageHandler, ServerRequestHandler
{
    private static final Logger LOG = Loggers.RAFT_LOGGER;

    // environment
    private final RaftConfiguration configuration;
    private final SocketAddress socketAddress;
    private final ClientTransport clientTransport;
    private final Random random = new Random();

    // persistent state
    private final LogStream logStream;
    private final RaftPersistentStorage persistentStorage;

    // volatile state
    private final BufferedLogStorageAppender appender;
    private final BufferingServerTransport serverTransport;
    private AbstractRaftState state;
    private final Map<SocketAddress, RaftMember> memberLookup = new HashMap<>();
    private final List<RaftMember> members = new ArrayList<>();
    private final List<RaftStateListener> raftStateListeners = new ArrayList<>();
    private boolean shouldElect = true;

    // controller
    private JoinController joinController;
    private AppendRaftEventController appendRaftEventController;

    private OpenLogStreamController openLogStreamController;
    private ReplicateLogController replicateLogController;
    private ConsensusRequestController pollController;
    private ConsensusRequestController voteController;

    // state message  handlers
    private final FollowerState followerState;
    private final CandidateState candidateState;
    private final LeaderState leaderState;

    // reused entities
    private final TransportMessage transportMessage = new TransportMessage();
    private final ServerResponse serverResponse = new ServerResponse();

    private final JoinRequest joinRequest = new JoinRequest();
    private final PollRequest pollRequest = new PollRequest();
    private final VoteRequest voteRequest = new VoteRequest();
    private final AppendRequest appendRequest = new AppendRequest();
    private final AppendResponse appendResponse = new AppendResponse();
    private ScheduledTimer electionTimer;
    private ScheduledTimer flushTimer;
    private String name;

    public Raft(final RaftConfiguration configuration, final SocketAddress socketAddress, final LogStream logStream,
                final BufferingServerTransport serverTransport, final ClientTransport clientTransport,
                final RaftPersistentStorage persistentStorage, final RaftStateListener... listeners)
    {
        name = String.format("%s - %s:%d", logStream.getLogName(), socketAddress.host(), socketAddress.port());
        this.configuration = configuration;
        this.socketAddress = socketAddress;
        this.logStream = logStream;
        this.clientTransport = clientTransport;
        this.persistentStorage = persistentStorage;
        appender = new BufferedLogStorageAppender(this);

        this.serverTransport = serverTransport;

        followerState = new FollowerState(this, appender);
        candidateState = new CandidateState(this, appender);
        leaderState = new LeaderState(this, appender, actor);

        raftStateListeners.addAll(Arrays.asList(listeners));

        followerState.reset();
        state = followerState;

        LOG.info("Created raft with configuration: " + this.configuration);
    }

    @Override
    public String getName()
    {
        return name;
    }

    public void registerRaftStateListener(final RaftStateListener listener)
    {
        actor.call(() -> raftStateListeners.add(listener));
    }

    public void removeRaftStateListener(final RaftStateListener listener)
    {
        actor.call(() -> raftStateListeners.remove(listener));
    }

    private void notifyRaftStateListener(final RaftStateListener listener)
    {
        listener.onStateChange(logStream.getPartitionId(), logStream.getTopicName(), socketAddress, state.getState());
    }

    public void notifyRaftStateListeners()
    {
        // only propagate state changes if the member is joined
        // otherwise members are already visible even if the join request was never accepted
        if (joinController.isJoined())
        {
            raftStateListeners.forEach(this::notifyRaftStateListener);
        }
    }

    public String getSubscriptionName()
    {
        return "raft-" + logStream.getLogName();
    }

    // state transitions

    public void becomeFollower()
    {
        followerState.reset();
        state = followerState;

        openLogStreamController.close();
        replicateLogController.close();
        pollController.close();
        voteController.close();

        scheduleElectionTimer();
        scheduleFlushTimer();

        notifyRaftStateListeners();

        LOG.debug("Transitioned to follower in term {}", getTerm());
    }

    private void scheduleFlushTimer()
    {
        if (flushTimer != null)
        {
            flushTimer.cancel();
        }
        flushTimer = actor.runAtFixedRate(configuration.getFlushInterval(), this::flushTimeoutCallback);
    }

    private void scheduleElectionTimer()
    {
        if (electionTimer != null)
        {
            electionTimer.cancel();
        }
        electionTimer = actor.runDelayed(nextElectionTimeout(), this::electionTimeoutCallback);
    }

    public void becomeCandidate()
    {
        candidateState.reset();
        state = candidateState;

        openLogStreamController.close();
        replicateLogController.close();
        pollController.close();
        voteController.sendRequest();

        scheduleElectionTimer();
        cancelFlushTimer();

        setTerm(getTerm() + 1);
        setVotedFor(socketAddress);

        notifyRaftStateListeners();

        LOG.debug("Transitioned to candidate in term {}", getTerm());
    }

    private void cancelFlushTimer()
    {
        if (flushTimer != null)
        {
            flushTimer.cancel();
            flushTimer = null;
        }
    }

    public void becomeLeader()
    {
        leaderState.reset();
        state = leaderState;

        cancelElectionTimer();
        cancelFlushTimer();

        openLogStreamController.open();
        replicateLogController.open();
        pollController.close();
        voteController.close();

        notifyRaftStateListeners();

        LOG.debug("Transitioned to leader in term {}", getTerm());
    }

    private void cancelElectionTimer()
    {
        if (electionTimer != null)
        {
            electionTimer.cancel();
            electionTimer = null;
        }
    }

    // actor
    @Override
    protected void onActorStarted()
    {
        joinController = new JoinController(this, actor);
        appendRaftEventController = new AppendRaftEventController(this, actor);

        openLogStreamController = new OpenLogStreamController(this, actor);
        replicateLogController = new ReplicateLogController(this, actor);

        pollController = new ConsensusRequestController(this, actor, new PollRequestHandler());
        voteController = new ConsensusRequestController(this, actor, new VoteRequestHandler());

        final ActorFuture<ServerInputSubscription> openSubscriptionFuture =
            serverTransport.openSubscription(getSubscriptionName(), this, this);

        actor.runOnCompletion(openSubscriptionFuture, (subscription, failure) ->
        {
            if (failure == null)
            {
                actor.consume(subscription, () ->
                {
                    if (subscription.poll() == 0)
                    {
                        actor.yield();
                    }
                });
            }
            else
            {
                LOG.error("Failed to appendEvent subscription", failure);
            }
        });

        // start as follower
        becomeFollower();

        actor.submit(joinController::join);

        if (members.isEmpty())
        {
            // !!!! WE NEED TO CANCEL THE ELECTION TIMER !!!
            // otherwise we will schedule the election twice
            electionTimer.cancel();
            actor.submit(this::electionTimeoutCallback);
        }
    }

    private void electionTimeoutCallback()
    {
        if (getState() != RaftState.LEADER)
        {
            if (shouldElect && joinController.isJoined())
            {
                switch (getState())
                {
                    case FOLLOWER:
                        LOG.debug("Triggering poll after election timeout reached");
                        becomeFollower();
                        // trigger a new poll immediately
                        pollController.sendRequest();
                        break;
                    case CANDIDATE:
                        LOG.debug("Triggering vote after election timeout reached");
                        // close current vote before starting the next
                        voteController.close();
                        becomeCandidate();
                        break;
                }
                LOG.debug("Election in state: {}", getState().name());
            }
            electionTimer = actor.runDelayed(nextElectionTimeout(), this::electionTimeoutCallback);
        }

        shouldElect = true;
    }

    private void flushTimeoutCallback()
    {
        appender.flushBufferedEvents();
    }

    public void skipNextElection()
    {
        shouldElect = false;
    }

    /**
     * Resets all controllers and closes appendEvent requests
     */
    public ActorFuture<Void> close()
    {
        final CompletableActorFuture<Void> closeFuture = new CompletableActorFuture<>();
        actor.call(() ->
        {
            LOG.debug("Shutdown raft.");
            actor.runOnCompletion(openLogStreamController.close(), (v, t) ->
            {
                actor.close();
                closeFuture.complete(null);
            });
            replicateLogController.close();

            pollController.close();
            voteController.close();

            leaderState.close();
            followerState.close();
            candidateState.close();

            appender.close();

            getMembers().forEach(RaftMember::close);
        });

        return closeFuture;
    }

    // message handler

    @Override
    public boolean onMessage(final ServerOutput output, final RemoteAddress remoteAddress, final DirectBuffer buffer, final int offset, final int length)
    {
        if (appendRequest.tryWrap(buffer, offset, length) && matchesLog(appendRequest))
        {
            state.appendRequest(appendRequest);
        }
        else if (appendResponse.tryWrap(buffer, offset, length) && matchesLog(appendResponse))
        {
            state.appendResponse(appendResponse);
        }

        return true;
    }

    @Override
    public boolean onRequest(final ServerOutput output, final RemoteAddress remoteAddress, final DirectBuffer buffer, final int offset, final int length, final long requestId)
    {
        if (joinRequest.tryWrap(buffer, offset, length) && matchesLog(joinRequest))
        {
            state.joinRequest(output, remoteAddress, requestId, joinRequest);
        }
        else if (pollRequest.tryWrap(buffer, offset, length) && matchesLog(pollRequest))
        {
            state.pollRequest(output, remoteAddress, requestId, pollRequest);
        }
        else if (voteRequest.tryWrap(buffer, offset, length) && matchesLog(voteRequest))
        {
            state.voteRequest(output, remoteAddress, requestId, voteRequest);
        }

        return true;
    }


    // environment

    public RaftConfiguration getConfiguration()
    {
        return configuration;
    }

    public SocketAddress getSocketAddress()
    {
        return socketAddress;
    }

    // state

    /**
     * @return the current {@link RaftState} of this raft node
     */
    public RaftState getState()
    {
        return state.getState();
    }

    public LogStream getLogStream()
    {
        return logStream;
    }

    /**
     * @return the current term of this raft node
     */
    public int getTerm()
    {
        return persistentStorage.getTerm();
    }

    /**
     * Update the term of this raft node, resetting the state of the raft for the new term.
     */
    public void setTerm(final int term)
    {
        final int currentTerm = getTerm();

        if (currentTerm < term)
        {
            persistentStorage
                .setTerm(term)
                .setVotedFor(null)
                .save();
        }
        else if (currentTerm > term)
        {
            LOG.debug("Cannot set term to smaller value {} < {}", term, currentTerm);
        }
    }

    /**
     * Checks if the raft term is still current, otherwise step down to become a follower
     * and update the current term.
     *
     * @return true if the current term is updated, false otherwise
     */
    public boolean mayStepDown(final HasTerm hasTerm)
    {
        final int messageTerm = hasTerm.getTerm();
        final int currentTerm = getTerm();

        if (currentTerm < messageTerm)
        {
            LOG.debug("Received message with higher term {} > {}", hasTerm.getTerm(), currentTerm);
            setTerm(messageTerm);
            becomeFollower();

            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * @return true if the message term is greater or equals of the current term
     */
    public boolean isTermCurrent(final HasTerm message)
    {
        return message.getTerm() >= getTerm();
    }

    /**
     * @return the raft which this node voted for in the current term, or null if not voted yet in the current term
     */
    public SocketAddress getVotedFor()
    {
        return persistentStorage.getVotedFor();
    }

    /**
     * @return true if not voted yet in the term or already vote the provided raft node
     */
    public boolean canVoteFor(final HasSocketAddress hasSocketAddress)
    {
        final SocketAddress votedFor = getVotedFor();
        return votedFor == null || votedFor.equals(hasSocketAddress.getSocketAddress());
    }

    /**
     * Set the raft which was granted a vote in the current term.
     */
    public void setVotedFor(final SocketAddress votedFor)
    {
        persistentStorage.setVotedFor(votedFor).save();
    }

    /**
     * @return the number of members known by this node, excluding itself
     */
    public int getMemberSize()
    {
        return members.size();
    }

    /**
     * @return the list of members known by this node, excluding itself
     */
    public List<RaftMember> getMembers()
    {
        return members;
    }

    public RaftMember getMember(final int index)
    {
        return members.get(index);
    }

    public RaftMember getMember(final SocketAddress socketAddress)
    {
        return memberLookup.get(socketAddress);
    }

    /**
     * @return true if the raft is know as member by this node, false otherwise
     */
    public boolean isMember(final SocketAddress socketAddress)
    {
        return memberLookup.get(socketAddress) != null;
    }

    /**
     * Replace existing members know by this node with new list of members
     */
    public void setMembers(final ValueArray<RaftConfigurationEventMember> members)
    {
        this.members.forEach(RaftMember::close);
        this.members.clear();
        this.memberLookup.clear();
        persistentStorage.clearMembers();

        final Iterator<RaftConfigurationEventMember> iterator = members.iterator();
        while (iterator.hasNext())
        {
            addMember(iterator.next().getSocketAddress());
        }

        persistentStorage.save();
    }


    /**
     * <p>
     * Add a list of raft nodes to the list of members known by this node if its not already
     * part of the members list.
     * </p>
     *
     * <p>
     * <b>Note:</b> If this node is part of the members list provided it will be ignored and not added to
     * the known members. This would distort the quorum determination.
     * </p>
     */
    public void addMembers(final List<SocketAddress> members)
    {
        for (int i = 0; i < members.size(); i++)
        {
            addMember(members.get(i));
        }

        persistentStorage.save();
    }

    /**
     *
     * @param socketAddress the address of the new member, the object is stored so it cannot be reused
     */
    private void addMember(final SocketAddress socketAddress)
    {
        ensureNotNull("Raft node socket address", socketAddress);

        if (socketAddress.equals(this.socketAddress))
        {
            return;
        }

        RaftMember member = getMember(socketAddress);

        if (member == null)
        {
            final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(socketAddress);

            member = new RaftMember(remoteAddress, logStream);
            member.reset();

            members.add(member);
            memberLookup.put(socketAddress, member);

            persistentStorage.addMember(socketAddress);
        }

    }

    /**
     *  Add raft to list of known members of this node and starts the {@link AppendRaftEventController} to write the new configuration to the log stream
     */
    public void joinMember(final ServerOutput serverOutput, final RemoteAddress remoteAddress, final long requestId, final SocketAddress socketAddress)
    {
        LOG.debug("New member {} joining the cluster", socketAddress);
        addMember(socketAddress);
        persistentStorage.save();

        appendRaftEventController.appendEvent(serverOutput, remoteAddress, requestId);
    }

    /**
     * @return the number which is required to reach a quorum based on the currently known members
     */
    public int requiredQuorum()
    {
        return Math.floorDiv(members.size() + 1, 2) + 1;
    }

    /**
     * @return true if the log stream controller is currently appendEvent, false otherwise
     */
    public boolean isLogStreamControllerOpen()
    {
        final LogStreamController logStreamController = logStream.getLogStreamController();
        return logStreamController != null && !logStreamController.isClosed();
    }

    /**
     * @return the position of the initial event of the term, -1 if the event is not written to the log yet
     */
    public long getInitialEventPosition()
    {
        return openLogStreamController.getPosition();
    }

    /**
     * @return true if the initial event of this node for the current term was committed to the log stream, false otherwise
     */
    public boolean isInitialEventCommitted()
    {
        return openLogStreamController.isPositionCommited();
    }

    /**
     * @return true if the last raft configuration event created by this node was committed to the log stream, false otherwise
     */
    public boolean isConfigurationEventCommitted()
    {
        return appendRaftEventController.isCommitted();
    }

    /**
     * @return the next election timeout starting from now
     */
    public Duration nextElectionTimeout()
    {
        final int electionIntervalMs = configuration.getElectionIntervalMs();
        return Duration.ofMillis(electionIntervalMs + (Math.abs(random.nextInt()) % electionIntervalMs));
    }

    /**
     * @return true if the partition id of the log stream matches the argument, false otherwise
     */
    public boolean matchesLog(final HasPartition hasPartition)
    {
        return logStream.getPartitionId() == hasPartition.getPartitionId();
    }

    // transport message sending

    /**
     * Send a {@link TransportMessage} to the given remote
     *
     * @return true if the message was written to the send buffer, false otherwise
     */
    public boolean sendMessage(final RemoteAddress remoteAddress, final BufferWriter writer)
    {
        transportMessage
            .reset()
            .remoteAddress(remoteAddress)
            .writer(writer);

        return clientTransport.getOutput().sendMessage(transportMessage);
    }

    /**
     * Send a {@link TransportMessage} to the given address
     *
     * @return true if the message was written to the send buffer, false otherwise
     */
    public void sendMessage(final SocketAddress socketAddress, final BufferWriter writer)
    {
        final RaftMember member = memberLookup.get(socketAddress);

        final RemoteAddress remoteAddress;
        if (member != null)
        {
            remoteAddress = member.getRemoteAddress();
        }
        else
        {
            remoteAddress = clientTransport.registerRemoteAddress(socketAddress);
        }

        sendMessage(remoteAddress, writer);
    }

    /**
     * Send a request to the given address
     *
     * @return the client request to poll for a response, or null if the request could not be written at the moment
     */
    public ActorFuture<ClientResponse> sendRequest(final RemoteAddress remoteAddress, final BufferWriter writer, Duration timeout)
    {
        return clientTransport.getOutput().sendRequest(remoteAddress, writer, timeout);
    }

    /**
     * Send a response over the given output to the given address
     *
     * @return true if the message was written to the send buffer, false otherwise
     */
    public void sendResponse(final ServerOutput serverOutput, final RemoteAddress remoteAddress, final long requestId, final BufferWriter writer)
    {
        serverResponse
            .reset()
            .remoteAddress(remoteAddress)
            .requestId(requestId)
            .writer(writer);

        serverOutput.sendResponse(serverResponse);
    }

    @Override
    public String toString()
    {
        return "raft-" + logStream.getLogName() + "-" + socketAddress.host() + ":" + socketAddress.port();
    }

}
