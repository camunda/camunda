/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.protocol;

import com.google.common.collect.Sets;
import io.atomix.cluster.MemberId;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.concurrent.ThreadContext;
import java.net.ConnectException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Test server protocol. */
public class TestRaftServerProtocol extends TestRaftProtocol implements RaftServerProtocol {

  private Function<JoinRequest, CompletableFuture<JoinResponse>> joinHandler;
  private Function<LeaveRequest, CompletableFuture<LeaveResponse>> leaveHandler;
  private Function<ConfigureRequest, CompletableFuture<ConfigureResponse>> configureHandler;
  private Function<ReconfigureRequest, CompletableFuture<ReconfigureResponse>> reconfigureHandler;
  private Function<InstallRequest, CompletableFuture<InstallResponse>> installHandler;
  private Function<TransferRequest, CompletableFuture<TransferResponse>> transferHandler;
  private Function<PollRequest, CompletableFuture<PollResponse>> pollHandler;
  private Function<VoteRequest, CompletableFuture<VoteResponse>> voteHandler;
  private Function<AppendRequest, CompletableFuture<AppendResponse>> appendHandler;
  private final Set<MemberId> partitions = Sets.newCopyOnWriteArraySet();

  public TestRaftServerProtocol(
      final MemberId memberId,
      final Map<MemberId, TestRaftServerProtocol> servers,
      final ThreadContext context) {
    super(servers, context);
    servers.put(memberId, this);
  }

  public void disconnect(final MemberId target) {
    partitions.add(target);
  }

  public void reconnect(final MemberId target) {
    partitions.remove(target);
  }

  @Override
  TestRaftServerProtocol server(final MemberId memberId) {
    if (partitions.contains(memberId)) {
      return null;
    }
    return super.server(memberId);
  }

  @Override
  public CompletableFuture<JoinResponse> join(final MemberId memberId, final JoinRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.join(request)));
  }

  @Override
  public CompletableFuture<LeaveResponse> leave(
      final MemberId memberId, final LeaveRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.leave(request)));
  }

  @Override
  public CompletableFuture<ConfigureResponse> configure(
      final MemberId memberId, final ConfigureRequest request) {
    return scheduleTimeout(
        getServer(memberId).thenCompose(listener -> listener.configure(request)));
  }

  @Override
  public CompletableFuture<ReconfigureResponse> reconfigure(
      final MemberId memberId, final ReconfigureRequest request) {
    return scheduleTimeout(
        getServer(memberId).thenCompose(listener -> listener.reconfigure(request)));
  }

  @Override
  public CompletableFuture<InstallResponse> install(
      final MemberId memberId, final InstallRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.install(request)));
  }

  @Override
  public CompletableFuture<TransferResponse> transfer(
      final MemberId memberId, final TransferRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.transfer(request)));
  }

  @Override
  public CompletableFuture<PollResponse> poll(final MemberId memberId, final PollRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.poll(request)));
  }

  @Override
  public CompletableFuture<VoteResponse> vote(final MemberId memberId, final VoteRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.vote(request)));
  }

  @Override
  public CompletableFuture<AppendResponse> append(
      final MemberId memberId, final AppendRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.append(request)));
  }

  @Override
  public void registerJoinHandler(
      final Function<JoinRequest, CompletableFuture<JoinResponse>> handler) {
    this.joinHandler = handler;
  }

  @Override
  public void unregisterJoinHandler() {
    this.joinHandler = null;
  }

  @Override
  public void registerLeaveHandler(
      final Function<LeaveRequest, CompletableFuture<LeaveResponse>> handler) {
    this.leaveHandler = handler;
  }

  @Override
  public void unregisterLeaveHandler() {
    this.leaveHandler = null;
  }

  @Override
  public void registerTransferHandler(
      final Function<TransferRequest, CompletableFuture<TransferResponse>> handler) {
    this.transferHandler = handler;
  }

  @Override
  public void unregisterTransferHandler() {
    this.transferHandler = null;
  }

  @Override
  public void registerConfigureHandler(
      final Function<ConfigureRequest, CompletableFuture<ConfigureResponse>> handler) {
    this.configureHandler = handler;
  }

  @Override
  public void unregisterConfigureHandler() {
    this.configureHandler = null;
  }

  @Override
  public void registerReconfigureHandler(
      final Function<ReconfigureRequest, CompletableFuture<ReconfigureResponse>> handler) {
    this.reconfigureHandler = handler;
  }

  @Override
  public void unregisterReconfigureHandler() {
    this.reconfigureHandler = null;
  }

  @Override
  public void registerInstallHandler(
      final Function<InstallRequest, CompletableFuture<InstallResponse>> handler) {
    this.installHandler = handler;
  }

  @Override
  public void unregisterInstallHandler() {
    this.installHandler = null;
  }

  @Override
  public void registerPollHandler(
      final Function<PollRequest, CompletableFuture<PollResponse>> handler) {
    this.pollHandler = handler;
  }

  @Override
  public void unregisterPollHandler() {
    this.pollHandler = null;
  }

  @Override
  public void registerVoteHandler(
      final Function<VoteRequest, CompletableFuture<VoteResponse>> handler) {
    this.voteHandler = handler;
  }

  @Override
  public void unregisterVoteHandler() {
    this.voteHandler = null;
  }

  @Override
  public void registerAppendHandler(
      final Function<AppendRequest, CompletableFuture<AppendResponse>> handler) {
    this.appendHandler = handler;
  }

  @Override
  public void unregisterAppendHandler() {
    this.appendHandler = null;
  }

  private CompletableFuture<TestRaftServerProtocol> getServer(final MemberId memberId) {
    final TestRaftServerProtocol server = server(memberId);
    if (server != null) {
      return Futures.completedFuture(server);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<AppendResponse> append(final AppendRequest request) {
    if (appendHandler != null) {
      return appendHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<VoteResponse> vote(final VoteRequest request) {
    if (voteHandler != null) {
      return voteHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<PollResponse> poll(final PollRequest request) {
    if (pollHandler != null) {
      return pollHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<TransferResponse> transfer(final TransferRequest request) {
    if (transferHandler != null) {
      return transferHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<InstallResponse> install(final InstallRequest request) {
    if (installHandler != null) {
      return installHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<ReconfigureResponse> reconfigure(final ReconfigureRequest request) {
    if (reconfigureHandler != null) {
      return reconfigureHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<ConfigureResponse> configure(final ConfigureRequest request) {
    if (configureHandler != null) {
      return configureHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<LeaveResponse> leave(final LeaveRequest request) {
    if (leaveHandler != null) {
      return leaveHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<JoinResponse> join(final JoinRequest request) {
    if (joinHandler != null) {
      return joinHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }
}
