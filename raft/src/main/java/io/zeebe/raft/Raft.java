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

import java.util.*;

import io.zeebe.logstreams.impl.LogStreamController;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.msgpack.value.ArrayValueIterator;
import io.zeebe.raft.controller.*;
import io.zeebe.raft.event.RaftConfigurationMember;
import io.zeebe.raft.protocol.*;
import io.zeebe.raft.state.*;
import io.zeebe.transport.*;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.buffer.BufferWriter;
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
public class Raft implements Actor, ServerMessageHandler, ServerRequestHandler
{

    public static final int HEARTBEAT_INTERVAL_MS = 250;
    public static final int ELECTION_INTERVAL_MS = 400;
    public static final int FLUSH_INTERVAL_MS = 500;

    // environment
    private final SocketAddress socketAddress;
    private final ClientTransport clientTransport;
    private final Logger logger;
    private final Random random = new Random();

    // persistent state
    private final LogStream logStream;
    private final RaftPersistentStorage persistentStorage;

    // volatile state
    private final BufferedLogStorageAppender appender;
    private AbstractRaftState state;
    private final Map<SocketAddress, RaftMember> memberLookup = new HashMap<>();
    private final List<RaftMember> members = new ArrayList<>();
    private Long electionTimeout;
    private Long flushTimeout;

    // controller
    private final SubscriptionController subscriptionController;
    private final JoinController joinController;
    private final AppendRaftEventController appendRaftEventController;

    private final OpenLogStreamController openLogStreamController;
    private final ReplicateLogController replicateLogController;
    private final PollController pollController;
    private final VoteController voteController;
    private final AdvanceCommitController advanceCommitController;

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

    public Raft(final SocketAddress socketAddress, final LogStream logStream, final BufferingServerTransport serverTransport, final ClientTransport clientTransport, final RaftPersistentStorage persistentStorage)
    {
        this.socketAddress = socketAddress;
        this.logStream = logStream;
        this.clientTransport = clientTransport;
        this.persistentStorage = persistentStorage;
        logger = Loggers.getRaftLogger(socketAddress, logStream);
        appender = new BufferedLogStorageAppender(this);

        subscriptionController = new SubscriptionController(this, serverTransport);
        joinController = new JoinController(this);
        appendRaftEventController = new AppendRaftEventController(this);

        openLogStreamController = new OpenLogStreamController(this);
        replicateLogController = new ReplicateLogController(this);
        pollController = new PollController(this);
        voteController = new VoteController(this);
        advanceCommitController = new AdvanceCommitController(this);

        followerState = new FollowerState(this, appender);
        candidateState = new CandidateState(this, appender);
        leaderState = new LeaderState(this, appender);

        // start as follower
        becomeFollower();

        // immediately try to join cluster
        joinController.open();
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

        appendRaftEventController.close();

        openLogStreamController.close();
        replicateLogController.close();
        pollController.close();
        voteController.close();
        advanceCommitController.close();

        resetElectionTimeout();
        resetFlushTimeout();

        logger.debug("Transitioned to follower in term {}", getTerm());
    }

    public void becomeCandidate()
    {
        candidateState.reset();
        state = candidateState;

        appendRaftEventController.close();

        openLogStreamController.close();
        replicateLogController.close();
        pollController.close();
        voteController.open();
        advanceCommitController.close();

        resetElectionTimeout();
        disableFlushTimeout();

        setTerm(getTerm() + 1);
        setVotedFor(socketAddress);

        logger.debug("Transitioned to candidate in term {}", getTerm());
    }

    public void becomeLeader()
    {
        leaderState.reset();
        state = leaderState;

        openLogStreamController.open();
        replicateLogController.open();
        pollController.close();
        voteController.close();
        advanceCommitController.open();

        disableElectionTimeout();
        disableFlushTimeout();

        logger.debug("Transitioned to leader in term {}", getTerm());
    }

    // actor

    public int doWork()
    {
        int workCount = 0;

        // poll for new messages
        workCount += subscriptionController.doWork();

        // check if election timeout occurred
        if (isElectionTimeout())
        {
            switch (getState())
            {
                case FOLLOWER:
                    logger.debug("Triggering poll after election timeout reached");
                    becomeFollower();
                    // trigger a new poll immediately
                    pollController.open();
                    break;
                case CANDIDATE:
                    logger.debug("Triggering vote after election timeout reached");
                    // close current vote before starting the next
                    voteController.close();
                    becomeCandidate();
                    break;
            }
        }

        // check if buffered events should be flushed
        if (isFlushTimeout())
        {
            resetFlushTimeout();
            appender.flushBufferedEvents();
        }

        // advance controllers
        workCount += joinController.doWork();
        workCount += appendRaftEventController.doWork();

        workCount += openLogStreamController.doWork();
        workCount += replicateLogController.doWork();
        workCount += pollController.doWork();
        workCount += voteController.doWork();
        workCount += advanceCommitController.doWork();

        return workCount;
    }

    /**
     * Resets all controllers and closes open requests
     */
    public void close()
    {
        joinController.reset();
        appendRaftEventController.reset();

        openLogStreamController.reset();
        replicateLogController.reset();
        pollController.reset();
        voteController.reset();
        advanceCommitController.reset();

        leaderState.close();
        followerState.close();
        candidateState.close();

        subscriptionController.reset();

        appender.close();

        getMembers().forEach(RaftMember::close);
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

    public SocketAddress getSocketAddress()
    {
        return socketAddress;
    }

    public Logger getLogger()
    {
        return logger;
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
            logger.debug("Cannot set term to smaller value {} < {}", term, currentTerm);
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
            logger.debug("Received message with higher term {} > {}", hasTerm.getTerm(), currentTerm);
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
    public void setMembers(final ArrayValueIterator<RaftConfigurationMember> members)
    {
        this.members.clear();
        this.memberLookup.clear();
        persistentStorage.clearMembers();

        while (members.hasNext())
        {
            addMember(members.next().getSocketAddress());
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

    private RaftMember addMember(final SocketAddress socketAddress)
    {
        ensureNotNull("Raft node socket address", socketAddress);

        if (socketAddress.equals(this.socketAddress))
        {
            return null;
        }

        RaftMember member = getMember(socketAddress);

        if (member == null)
        {
            final SocketAddress address = new SocketAddress(socketAddress);
            final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(address);

            member = new RaftMember(remoteAddress, logStream);
            member.reset(nextHeartbeat());

            members.add(member);
            memberLookup.put(address, member);

            persistentStorage.addMember(socketAddress);
        }

        return member;
    }

    /**
     *  Add raft to list of known members of this node and starts the {@link AppendRaftEventController} to write the new configuration to the log stream
     */
    public void joinMember(final ServerOutput serverOutput, final RemoteAddress remoteAddress, final long requestId, final SocketAddress socketAddress)
    {
        logger.debug("New member {} joining the cluster", socketAddress);
        addMember(socketAddress);
        persistentStorage.save();
        appendRaftEventController.open(serverOutput, remoteAddress, requestId);
    }

    /**
     * @return the number which is required to reach a quorum based on the currently known members
     */
    public int requiredQuorum()
    {
        return Math.floorDiv(members.size() + 1, 2) + 1;
    }

    /**
     * @return true if the log stream controller is currently open, false otherwise
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
        return openLogStreamController.getInitialEventPosition();
    }

    /**
     * @return true if the initial event of this node for the current term was committed to the log stream, false otherwise
     */
    public boolean isInitialEventCommitted()
    {
        return openLogStreamController.isCommitted();
    }

    /**
     * @return true if the last raft configuration event created by this node was committed to the log stream, false otherwise
     */
    public boolean isConfigurationEventCommitted()
    {
        return appendRaftEventController.isCommitted();
    }

    /**
     * Stop the election timeout for this raft, i.e. in the leader state no elections should be triggered
     */
    private void disableElectionTimeout()
    {
        electionTimeout = null;
    }

    /**
     * @return true if the raft should start a new election, false otherwise
     */
    private boolean isElectionTimeout()
    {
        return electionTimeout != null && joinController.isJoined() && electionTimeout < System.currentTimeMillis();
    }

    /**
     * Resets the election timeout to the next period
     */
    public void resetElectionTimeout()
    {
        electionTimeout = nextElectionTimeout();
    }

    /**
     * @return the next election timeout starting from now
     */
    public long nextElectionTimeout()
    {
        return System.currentTimeMillis() + ELECTION_INTERVAL_MS + (Math.abs(random.nextInt()) % ELECTION_INTERVAL_MS);
    }

    /**
     * @return the next heartbeat timeout starting from now
     */
    public long nextHeartbeat()
    {
        return System.currentTimeMillis() + HEARTBEAT_INTERVAL_MS;
    }

    /**
     * Stop the election timeout for this raft, i.e. in the leader state no elections should be triggered
     */
    private void disableFlushTimeout()
    {
        flushTimeout = null;
    }

    /**
     * @return true if the raft should start a new flush, false otherwise
     */
    private boolean isFlushTimeout()
    {
        return flushTimeout != null && flushTimeout < System.currentTimeMillis();
    }

    /**
     * Resets the flush timeout to the next period
     */
    public void resetFlushTimeout()
    {
        flushTimeout = nextFlushTimeout();
    }

    /**
     * @return the next heartbeat timeout starting from now
     */
    public long nextFlushTimeout()
    {
        return System.currentTimeMillis() + FLUSH_INTERVAL_MS;
    }

    /**
     * @return true if the topic name and partition id of the log stream matches the argument, false otherwise
     */
    public boolean matchesLog(final HasTopic hasTopic)
    {
        final DirectBuffer topicName = logStream.getTopicName();
        final int partitionId = logStream.getPartitionId();

        return topicName.equals(hasTopic.getTopicName()) && partitionId == hasTopic.getPartitionId();
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
    public boolean sendMessage(final SocketAddress socketAddress, final BufferWriter writer)
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

        return sendMessage(remoteAddress, writer);
    }

    /**
     * Send a request to the given address
     *
     * @return the client request to poll for a response, or null if the request could not be written at the moment
     */
    public ClientRequest sendRequest(final RemoteAddress remoteAddress, final BufferWriter writer)
    {
        return clientTransport.getOutput().sendRequest(remoteAddress, writer);
    }

    /**
     * Send a response over the given output to the given address
     *
     * @return true if the message was written to the send buffer, false otherwise
     */
    public boolean sendResponse(final ServerOutput serverOutput, final RemoteAddress remoteAddress, final long requestId, final BufferWriter writer)
    {
        serverResponse
            .reset()
            .remoteAddress(remoteAddress)
            .requestId(requestId)
            .writer(writer);

        return serverOutput.sendResponse(serverResponse);
    }

    @Override
    public String toString()
    {
        return "raft-" + logStream.getLogName() + "-" + socketAddress.host() + ":" + socketAddress.port();
    }
}
