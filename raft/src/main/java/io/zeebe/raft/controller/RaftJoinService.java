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
package io.zeebe.raft.controller;

import io.zeebe.raft.Loggers;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftMember;
import io.zeebe.raft.RaftMembers;
import io.zeebe.raft.protocol.ConfigurationRequest;
import io.zeebe.raft.protocol.ConfigurationResponse;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ClientResponse;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class RaftJoinService implements Service<Void> {
  private static final Logger LOG = Loggers.RAFT_LOGGER;

  public static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(500);
  public static final Duration DEFAULT_RETRY = Duration.ofMillis(200);

  private final ActorControl actor;
  private final ConfigurationRequest configurationRequest = new ConfigurationRequest();
  private final ConfigurationResponse configurationResponse = new ConfigurationResponse();

  private final Raft raft;
  private final RaftMembers raftMembers;

  // will not be reset to continue to select new members on every retry
  private int currentMember;
  private final CompletableActorFuture<Void> whenJoinCompleted = new CompletableActorFuture<>();
  private final CompletableActorFuture<Void> whenLeaveCompleted = new CompletableActorFuture<>();

  private final Duration leaveTimeout;

  public RaftJoinService(final Raft raft, ActorControl actorControl) {
    this.actor = actorControl;
    this.raft = raft;
    this.raftMembers = raft.getRaftMembers();
    this.leaveTimeout = raft.getConfiguration().getLeaveTimeoutDuration();
  }

  @Override
  public void start(ServiceStartContext startContext) {
    startContext.async(whenJoinCompleted, true);
    actor.call(this::join);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    if (!stopContext.wasInterrupted()) {
      stopContext.async(whenLeaveCompleted);
      actor.call(this::leave);
    }
  }

  public void join() {
    final Runnable onJoined =
        () -> {
          LOG.info("Joined raft in term {}", raft.getTerm());
          // set initial heartbeat as we received a message from the leader
          raft.getHeartbeat().update();
          whenJoinCompleted.complete(null);
        };

    final Consumer<Integer> joinRequestConfigurator =
        (nextMember) -> {
          LOG.debug("Send join configuration request to node {}", nextMember);
          configurationRequest.reset().setRaft(raft);
        };

    sendConfigurationRequest(joinRequestConfigurator, onJoined);
  }

  public void leave() {
    if (raft.getState() == RaftState.LEADER) {
      whenLeaveCompleted.completeExceptionally(
          new UnsupportedOperationException("Leave not yet implemented for leader"));
      return;
    }

    final Runnable onLeaveCluster =
        () -> {
          if (!whenLeaveCompleted.isDone()) {
            raft.notifyRaftStateListeners();
            whenLeaveCompleted.complete(null);
          }
        };

    sendConfigurationRequest(
        (nextMember) -> {
          LOG.debug("Send leave configuration request to {}", nextMember);
          configurationRequest.reset().setRaft(raft).setLeave();
        },
        onLeaveCluster);

    actor.runDelayed(
        leaveTimeout,
        () -> {
          if (!whenLeaveCompleted.isDone()) {
            final String timeoutMessage = "Timeout while leaving raft cluster.";
            LOG.warn(timeoutMessage);
            whenLeaveCompleted.completeExceptionally(new RuntimeException(timeoutMessage));
          }
        });
  }

  private void sendConfigurationRequest(
      Consumer<Integer> configureRequest, Runnable configurationAcceptedCallback) {
    final Integer nextMember = getNextMember();

    if (nextMember != null) {
      configureRequest.accept(nextMember);

      final ActorFuture<ClientResponse> responseFuture =
          raft.sendRequest(nextMember, configurationRequest, DEFAULT_TIMEOUT);

      actor.runOnCompletion(
          responseFuture,
          ((response, throwable) -> {
            if (throwable == null) {
              final DirectBuffer responseBuffer = response.getResponseBuffer();
              configurationResponse.wrap(responseBuffer, 0, responseBuffer.capacity());

              if (!raft.mayStepDown(configurationResponse)
                  && raft.isTermCurrent(configurationResponse)) {
                // update members to maybe discover leader
                raft.addMembersWhenJoined(configurationResponse.getMembers());

                if (configurationResponse.isSucceeded()) {
                  LOG.debug(
                      "Configuration request was accepted in term {}",
                      configurationResponse.getTerm());
                  configurationAcceptedCallback.run();
                } else {
                  LOG.debug(
                      "Configuration request was rejected in term {}",
                      configurationResponse.getTerm());
                  actor.runDelayed(
                      DEFAULT_RETRY,
                      () ->
                          sendConfigurationRequest(
                              configureRequest, configurationAcceptedCallback));
                }
              } else {
                LOG.debug("Configuration response with different term.");
                // received response from different term
                actor.runDelayed(
                    DEFAULT_RETRY,
                    () ->
                        sendConfigurationRequest(configureRequest, configurationAcceptedCallback));
              }
            } else {
              LOG.debug("Failed to send configuration request to {}", nextMember);
              actor.runDelayed(
                  DEFAULT_RETRY,
                  () -> sendConfigurationRequest(configureRequest, configurationAcceptedCallback));
            }
          }));
    } else {
      LOG.debug("Ignoring configuration request in single node cluster in term {}", raft.getTerm());
      configurationAcceptedCallback.run();
    }
  }

  private Integer getNextMember() {
    final List<RaftMember> memberList = raftMembers.getMemberList();
    final int memberSize = memberList.size();
    if (memberSize > 0) {
      final int nextMember = currentMember % memberSize;
      currentMember++;

      return memberList.get(nextMember).getNodeId();
    } else {
      return null;
    }
  }

  @Override
  public Void get() {
    return null;
  }

  public boolean isJoined() {
    return whenJoinCompleted.isDone() && !whenJoinCompleted.isCompletedExceptionally();
  }
}
