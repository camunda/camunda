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

import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logStorageCommitListenerServiceName;
import static io.zeebe.raft.RaftServiceNames.candidateServiceName;
import static io.zeebe.raft.RaftServiceNames.followerServiceName;
import static io.zeebe.raft.RaftServiceNames.joinServiceName;
import static io.zeebe.raft.RaftServiceNames.leaderInitialEventCommittedServiceName;
import static io.zeebe.raft.RaftServiceNames.leaderInstallServiceName;
import static io.zeebe.raft.RaftServiceNames.leaderOpenLogStreamServiceName;
import static io.zeebe.raft.RaftServiceNames.leaderServiceName;
import static io.zeebe.raft.RaftServiceNames.pollServiceName;
import static io.zeebe.raft.state.RaftTranisiton.TO_CANDIDATE;
import static io.zeebe.raft.state.RaftTranisiton.TO_FOLLOWER;
import static io.zeebe.raft.state.RaftTranisiton.TO_LEADER;

import io.zeebe.logstreams.impl.service.LeaderOpenLogStreamAppenderService;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.raft.controller.AppendRaftEventController;
import io.zeebe.raft.controller.LeaderCommitInitialEvent;
import io.zeebe.raft.controller.RaftJoinService;
import io.zeebe.raft.controller.RaftPollService;
import io.zeebe.raft.event.RaftConfigurationEventMember;
import io.zeebe.raft.protocol.HasNodeId;
import io.zeebe.raft.protocol.HasTerm;
import io.zeebe.raft.state.AbstractRaftState;
import io.zeebe.raft.state.CandidateState;
import io.zeebe.raft.state.FollowerState;
import io.zeebe.raft.state.Heartbeat;
import io.zeebe.raft.state.LeaderState;
import io.zeebe.raft.state.RaftState;
import io.zeebe.raft.state.Transition;
import io.zeebe.servicecontainer.CompositeServiceBuilder;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerRequestHandler;
import io.zeebe.transport.ServerResponse;
import io.zeebe.transport.impl.actor.Receiver;
import io.zeebe.util.LogUtil;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.channel.ConcurrentQueueChannel;
import io.zeebe.util.sched.channel.OneToOneRingBufferChannel;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

/** Representation of a member of a raft cluster. */
public class Raft extends Actor
    implements ServerMessageHandler, ServerRequestHandler, Service<Raft> {
  private static final Logger LOG = Loggers.RAFT_LOGGER;
  private static final boolean IS_TRACE_ENABLED = LOG.isTraceEnabled();

  private final Injector<LogStream> logStreamInjector = new Injector<>();

  // environment
  private final ConcurrentQueueChannel<IncomingRaftRequest> requestQueue =
      new ConcurrentQueueChannel<>(new ManyToOneConcurrentLinkedQueue<>());
  private final OneToOneRingBufferChannel messageReceiveBuffer;
  private final RaftConfiguration configuration;
  private final int nodeId;
  private final ClientTransport clientTransport;

  private ServiceName<?> currentStateServiceName;

  // State Machine
  private Transition currentTransition;
  private Transition nextTransition;

  // persistent state
  private LogStream logStream;
  private final RaftPersistentStorage persistentStorage;

  // volatile state
  private final List<RaftStateListener> raftStateListeners = new ArrayList<>();
  private final Heartbeat heartbeat;
  private final RaftMembers raftMembers;

  // reused entities
  private final ServerResponse serverResponse = new ServerResponse();

  private ServiceStartContext serviceContext;

  private final String raftName;
  private AbstractRaftState state;
  private RaftJoinService raftJoinedService;

  public Raft(
      final String raftName,
      final RaftConfiguration configuration,
      final int nodeId,
      final ClientTransport clientTransport,
      final RaftPersistentStorage persistentStorage,
      final OneToOneRingBufferChannel messageReceiveBuffer,
      final RaftStateListener... listeners) {
    this.configuration = configuration;
    this.nodeId = nodeId;
    this.clientTransport = clientTransport;
    this.persistentStorage = persistentStorage;
    this.messageReceiveBuffer = messageReceiveBuffer;
    this.raftName = raftName;

    this.heartbeat = new Heartbeat(configuration.getElectionIntervalDuration().toMillis());
    this.raftMembers = new RaftMembers(nodeId, persistentStorage);

    raftStateListeners.addAll(Arrays.asList(listeners));

    LOG.info("Created raft {} with configuration {}", raftName, configuration);
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    this.logStream = logStreamInjector.getValue();
    this.logStream.setTerm(getTerm());

    this.serviceContext = startContext;

    raftJoinedService = new RaftJoinService(this, actor);
    serviceContext.createService(joinServiceName(raftName), raftJoinedService).install();

    startContext.async(startContext.getScheduler().submitActor(this, true));
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    stopContext.async(actor.close());
  }

  @Override
  protected void onActorStarted() {
    state = null;
    becomeFollower(getTerm());
  }

  // state transitions

  public void becomeFollower(final int term) {
    final Transition transition = new Transition(TO_FOLLOWER, term);
    tryTakeTransition(transition, this::transitionToFollower);
  }

  public void becomeCandidate(final int term) {
    final Transition transition = new Transition(TO_CANDIDATE, term);
    tryTakeTransition(transition, this::transitionToCandidate);
  }

  public void becomeLeader(final int term) {
    final Transition transition = new Transition(TO_LEADER, term);
    tryTakeTransition(transition, this::transitionToLeader);
  }

  private void transitionToFollower(final Void value, final Throwable throwable) {
    final int term = currentTransition.getTerm();

    state = null;

    setTerm(term);

    final ServiceName<AbstractRaftState> followerServiceName = followerServiceName(raftName, term);
    final FollowerState followerState = new FollowerState(this, actor);

    final ServiceName<Void> pollServiceName = pollServiceName(raftName, term);
    final RaftPollService pollService = new RaftPollService(this, actor);

    serviceContext
        .createService(pollServiceName, pollService)
        .dependency(joinServiceName(raftName))
        .dependency(followerServiceName)
        .install();

    final ActorFuture<AbstractRaftState> installFuture =
        serviceContext.createService(followerServiceName, followerState).install();

    actor.runOnCompletion(installFuture, this::onStateTransitionCompleted);

    currentStateServiceName = followerServiceName;
  }

  private void transitionToCandidate(final Void value, final Throwable throwable) {
    final int term = currentTransition.getTerm();

    state = null;

    setTerm(term);
    setVotedFor(getNodeId());

    final ServiceName<AbstractRaftState> candidateServiceName =
        candidateServiceName(raftName, term);
    final CandidateState candidateState = new CandidateState(this, actor);

    final ActorFuture<AbstractRaftState> whenCandicate =
        serviceContext
            .createService(candidateServiceName, candidateState)
            .dependency(joinServiceName(raftName))
            .install();

    actor.runOnCompletion(whenCandicate, this::onStateTransitionCompleted);

    currentStateServiceName = candidateServiceName;
  }

  private void transitionToLeader(final Void value, final Throwable throwable) {
    final int term = currentTransition.getTerm();

    state = null;

    final ServiceName<Void> installOperationServiceName = leaderInstallServiceName(raftName, term);
    final ServiceName<AbstractRaftState> leaderServiceName = leaderServiceName(raftName, term);
    final ServiceName<Void> initialEventCommittedServiceName =
        leaderInitialEventCommittedServiceName(raftName, term);
    final ServiceName<Void> openLogStreamServiceName =
        leaderOpenLogStreamServiceName(raftName, term);

    final CompositeServiceBuilder installOperation =
        serviceContext.createComposite(installOperationServiceName);

    installOperation
        .createService(openLogStreamServiceName, new LeaderOpenLogStreamAppenderService(logStream))
        .install();

    final LeaderState leaderState = new LeaderState(this, actor);
    installOperation
        .createService(leaderServiceName, leaderState)
        .dependency(LogStreamServiceNames.logWriteBufferServiceName(logStream.getLogName()))
        .dependency(openLogStreamServiceName)
        .dependency(joinServiceName(raftName))
        .dependency(logStorageCommitListenerServiceName(raftName))
        .install();

    final LeaderCommitInitialEvent leaderCommitInitialEventService =
        new LeaderCommitInitialEvent(this, actor, leaderState);
    serviceContext
        .createService(initialEventCommittedServiceName, leaderCommitInitialEventService)
        .dependency(leaderServiceName)
        .install();

    final ActorFuture<Void> whenLeader = installOperation.install();

    actor.runOnCompletion(
        whenLeader, ((v, t) -> onStateTransitionCompleted(leaderState, throwable)));

    currentStateServiceName = installOperationServiceName;
  }

  private void tryTakeTransition(
      final Transition transition, final BiConsumer<Void, Throwable> whenLeftCallback) {
    if (currentTransition != null) {
      if (!currentTransition.equals(transition)) {
        nextTransition = transition;
        LOG.debug(
            "Setting next transition {} during transition {} from state {}",
            nextTransition,
            currentTransition,
            getState());
      }
    } else if (!transition.isValid(getState(), getTerm())) {
      LOG.warn(
          "Ignoring invalid transition {} in state {} and term {}",
          transition,
          getState(),
          getTerm());
    } else {
      currentTransition = transition;
      LOG.debug("Taking transition {} from state {}", transition, getState());
      leaveState(whenLeftCallback);
    }
  }

  private void leaveState(final BiConsumer<Void, Throwable> whenLeftCallback) {
    final ActorFuture<Void> whenPrevStateLeft;
    if (currentStateServiceName != null) {
      whenPrevStateLeft = serviceContext.removeService(currentStateServiceName);
    } else {
      whenPrevStateLeft = CompletableActorFuture.completed(null);
    }

    actor.runOnCompletion(whenPrevStateLeft, whenLeftCallback);
  }

  private void onStateTransitionCompleted(
      final AbstractRaftState state, final Throwable throwable) {
    final Transition lastTransititon = currentTransition;
    currentTransition = null;

    if (throwable == null) {
      this.state = state;

      LOG.debug("Transitioned to state {} in term {}", getState(), getTerm());

      if (nextTransition != null) {
        final Transition transition = nextTransition;
        nextTransition = null;

        if (transition.isValid(getState(), getTerm())) {
          switch (transition.getRaftTranisiton()) {
            case TO_FOLLOWER:
              becomeFollower(transition.getTerm());
              break;
            case TO_CANDIDATE:
              becomeCandidate(transition.getTerm());
              break;
            case TO_LEADER:
              becomeLeader(transition.getTerm());
              break;
            default:
              LOG.warn(
                  "Ignoring unknown transition {} in state {} and term {}",
                  transition,
                  getState(),
                  getTerm());
              notifyRaftStateListeners();
              break;
          }
        } else {
          LOG.debug(
              "Ignoring invalid next transition {} in state {} and term {}",
              transition,
              getState(),
              getTerm());
          notifyRaftStateListeners();
        }
      } else {
        notifyRaftStateListeners();
      }
    } else {
      LOG.error(
          "Failed to execute transition {}, raft is now in an undefined state", lastTransititon);
    }
  }

  // listeners

  public void notifyRaftStateListeners() {
    raftStateListeners.forEach(
        (l) -> LogUtil.catchAndLog(LOG, () -> l.onStateChange(this, getState())));
  }

  private List<ActorFuture<Void>> notifyMemberLeavingListeners(final List<Integer> newMembers) {
    final List<ActorFuture<Void>> futures = new ArrayList<>();

    raftStateListeners.forEach(
        (l) -> LogUtil.catchAndLog(LOG, () -> futures.add(l.onMemberLeaving(this, newMembers))));

    return futures;
  }

  private void notifyMemberJoinedListeners() {
    final List<Integer> memberNodeIds = raftMembers.getMemberIds();

    raftStateListeners.forEach(
        (l) -> LogUtil.catchAndLog(LOG, () -> l.onMemberJoined(this, memberNodeIds)));
  }

  // message handler

  /** called by the transport {@link Receiver} */
  @Override
  public boolean onMessage(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length) {
    if (IS_TRACE_ENABLED) {
      LOG.trace("Received message from {}", remoteAddress);
    }

    // update heartbeat in receiver actor so that it reflects when last message
    // was received
    heartbeat.update();

    final boolean isWritten = messageReceiveBuffer.write(1, buffer, offset, length);

    if (!isWritten) {
      LOG.warn("dropping");
    }

    return true;
  }

  /** called by the transport {@link Receiver} */
  @Override
  public boolean onRequest(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final long requestId) {
    final MutableDirectBuffer requestData = new UnsafeBuffer(new byte[length]);
    requestData.putBytes(0, buffer, offset, length);

    return requestQueue.offer(
        new IncomingRaftRequest(output, remoteAddress, requestId, requestData));
  }

  // environment

  public RaftConfiguration getConfiguration() {
    return configuration;
  }

  public int getNodeId() {
    return nodeId;
  }

  // state

  /** @return the current {@link RaftState} of this raft node */
  public RaftState getState() {
    return state == null ? null : state.getState();
  }

  public LogStream getLogStream() {
    return logStream;
  }

  /** @return the current term of this raft node */
  public int getTerm() {
    return persistentStorage.getTerm();
  }

  /** Update the term of this raft node, resetting the state of the raft for the new term. */
  public void setTerm(final int term) {
    final int currentTerm = getTerm();

    if (currentTerm < term) {
      persistentStorage.setTerm(term).setVotedFor(null).save();

      logStream.setTerm(term);
    } else if (currentTerm > term) {
      LOG.debug("Cannot set term to smaller value {} < {}", term, currentTerm);
    }
  }

  public boolean isJoined() {
    return raftJoinedService.isJoined();
  }

  /**
   * Checks if the raft term is still current, otherwise step down to become a follower and update
   * the current term.
   *
   * @return true if the current term is updated, false otherwise
   */
  public boolean mayStepDown(final HasTerm hasTerm) {
    final int messageTerm = hasTerm.getTerm();
    final int currentTerm = getTerm();

    if (currentTerm < messageTerm) {
      LOG.debug("Received message with higher term {} > {}", hasTerm.getTerm(), currentTerm);

      becomeFollower(messageTerm);
      return true;
    } else {
      return false;
    }
  }

  /** @return true if the message term is greater or equals of the current term */
  public boolean isTermCurrent(final HasTerm message) {
    return message.getTerm() >= getTerm();
  }

  /**
   * @return the raft which this node voted for in the current term, or null if not voted yet in the
   *     current term
   */
  public Integer getVotedFor() {
    return persistentStorage.getVotedFor();
  }

  /** @return true if not voted yet in the term or already vote the provided raft node */
  public boolean canVoteFor(final HasNodeId hasNodeId) {
    final Integer votedFor = getVotedFor();
    return votedFor == null || votedFor.equals(hasNodeId.getNodeId());
  }

  /** Set the raft which was granted a vote in the current term. */
  public void setVotedFor(final int nodeId) {
    persistentStorage.setVotedFor(nodeId).save();
  }

  public RaftMembers getRaftMembers() {
    return raftMembers;
  }

  public int getMemberSize() {
    return raftMembers.getMemberSize();
  }

  /** Replace existing members know by this node with new list of members */
  public void replaceMembersOnConfigurationChange(
      final ValueArray<RaftConfigurationEventMember> members) {
    raftMembers.replaceMembersOnConfigurationChange(members);
    notifyMemberJoinedListeners();
  }

  /**
   * Add a list of raft nodes to the list of members known by this node if its not already part of
   * the members list.
   *
   * <p><b>Note:</b> If this node is part of the members list provided it will be ignored and not
   * added to the known members. This would distort the quorum determination.
   */
  public void addMembersWhenJoined(final List<Integer> members) {
    raftMembers.addMembersWhenJoined(members);
    notifyMemberJoinedListeners();
  }

  /**
   * Add raft to list of known members of this node and starts the {@link AppendRaftEventController}
   * to write the new configuration to the log stream
   */
  public boolean joinMember(final int nodeId) {
    LOG.debug("New member {} joining the cluster", nodeId);
    final RaftMember newMember = raftMembers.addMember(nodeId);

    if (newMember != null && state.getState() == RaftState.LEADER) {

      notifyMemberJoinedListeners();

      return true;
    }
    return false;
  }

  public ActorFuture<Boolean> memberLeaves(final int nodeId) {
    final CompletableActorFuture<Boolean> leaveFuture = new CompletableActorFuture<>();

    LOG.debug("Member {} leaving the cluster", nodeId);

    if (raftMembers.hasMember(nodeId)) {
      final List<Integer> listWithoutMemberThatIsLeaving = raftMembers.getMemberIds();

      listWithoutMemberThatIsLeaving.removeIf(member -> member.equals(nodeId));

      actor.runOnCompletion(
          notifyMemberLeavingListeners(listWithoutMemberThatIsLeaving),
          (t) -> {
            if (state.getState() == RaftState.LEADER) {
              raftMembers.removeMember(nodeId);

              leaveFuture.complete(true);
            } else {
              leaveFuture.complete(false);
            }
          });
    } else {
      leaveFuture.complete(false);
    }

    return leaveFuture;
  }

  /** @return the number which is required to reach a quorum based on the currently known members */
  public int requiredQuorum() {
    return RaftMath.getRequiredQuorum(getMemberSize() + 1);
  }

  // transport message sending

  /**
   * Send a {@link TransportMessage} to the given remote
   *
   * @return true if the message was written to the send buffer, false otherwise
   */
  public boolean sendMessage(final int nodeId, final BufferWriter writer) {
    return clientTransport.getOutput().sendMessage(nodeId, writer);
  }

  /**
   * Send a request to the given address
   *
   * @return the client request to poll for a response, or null if the request could not be written
   *     at the moment
   */
  public ActorFuture<ClientResponse> sendRequest(
      final int nodeId, final BufferWriter writer, final Duration timeout) {
    return clientTransport.getOutput().sendRequest(nodeId, writer, timeout);
  }

  /** Send a response over the given output to the given address */
  public void sendResponse(
      final ServerOutput serverOutput,
      final RemoteAddress remoteAddress,
      final long requestId,
      final BufferWriter writer) {
    serverResponse.reset().remoteAddress(remoteAddress).requestId(requestId).writer(writer);

    serverOutput.sendResponse(serverResponse);
  }

  @Override
  public String toString() {
    return "raft-" + logStream.getLogName() + "-" + nodeId;
  }

  /** TODO: needed for testing(?) */
  public ActorFuture<Void> clearReceiveBuffer() {
    return actor.call(
        () -> {
          messageReceiveBuffer.read(
              (msgTypeId, buffer, index, length) -> {
                // discard
              });
        });
  }

  public int getPartitionId() {
    return logStream.getPartitionId();
  }

  public int getReplicationFactor() {
    return persistentStorage.getReplicationFactor();
  }

  public void registerRaftStateListener(final RaftStateListener listener) {
    actor.run(() -> raftStateListeners.add(listener));
  }

  public void unregisterRaftStateListener(final RaftStateListener listener) {
    actor.run(() -> raftStateListeners.remove(listener));
  }

  @Override
  public Raft get() {
    return this;
  }

  public ConcurrentQueueChannel<IncomingRaftRequest> getRequestQueue() {
    return requestQueue;
  }

  public OneToOneRingBufferChannel getMessageReceiveBuffer() {
    return messageReceiveBuffer;
  }

  @Override
  public String getName() {
    return raftName;
  }

  public Heartbeat getHeartbeat() {
    return heartbeat;
  }

  public Injector<LogStream> getLogStreamInjector() {
    return logStreamInjector;
  }
}
