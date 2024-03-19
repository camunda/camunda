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
import io.atomix.utils.concurrent.ThreadContext;
import java.net.ConnectException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Test server protocol. */
public class TestRaftServerProtocol extends TestRaftProtocol implements RaftServerProtocol {

  private Function<ConfigureRequest, CompletableFuture<ConfigureResponse>> configureHandler;
  private Function<ReconfigureRequest, CompletableFuture<ReconfigureResponse>> reconfigureHandler;
  private Function<InstallRequest, CompletableFuture<InstallResponse>> installHandler;
  private Function<TransferRequest, CompletableFuture<TransferResponse>> transferHandler;
  private Function<PollRequest, CompletableFuture<PollResponse>> pollHandler;
  private Function<VoteRequest, CompletableFuture<VoteResponse>> voteHandler;
  private Function<AppendRequest, CompletableFuture<AppendResponse>> appendHandler;
  private final Set<MemberId> partitions = Sets.newCopyOnWriteArraySet();
<<<<<<< HEAD:atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
=======
  private final Map<Class<?>, Consumer<?>> interceptors = new ConcurrentHashMap<>();
  private final Map<MemberId, TestRaftServerProtocol> servers;
  private final Map<Class<?>, ResponseInterceptor<?>> responseInterceptors =
      new ConcurrentHashMap<>();
>>>>>>> 281eb95c (test: verify install requests retry behavior on timeout):zeebe/atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java

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
  public CompletableFuture<ConfigureResponse> configure(
      final MemberId memberId, final ConfigureRequest request) {
<<<<<<< HEAD:atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
    return scheduleTimeout(
        getServer(memberId).thenCompose(listener -> listener.configure(request)));
=======
    intercept(request, ConfigureRequest.class);
    return getServer(memberId)
        .thenCompose(listener -> listener.configure(request))
        .thenCompose(response -> transformResponse(response, ConfigureResponse.class))
        .orTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
>>>>>>> 281eb95c (test: verify install requests retry behavior on timeout):zeebe/atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
  }

  @Override
  public CompletableFuture<ReconfigureResponse> reconfigure(
      final MemberId memberId, final ReconfigureRequest request) {
<<<<<<< HEAD:atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
    return scheduleTimeout(
        getServer(memberId).thenCompose(listener -> listener.reconfigure(request)));
=======
    intercept(request, ReconfigureRequest.class);
    return getServer(memberId)
        .thenCompose(listener -> listener.reconfigure(request))
        .thenCompose(response -> transformResponse(response, ReconfigureResponse.class))
        .orTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  @Override
  public CompletableFuture<ForceConfigureResponse> forceConfigure(
      final MemberId memberId, final ForceConfigureRequest request) {
    intercept(request, ForceConfigureRequest.class);
    return getServer(memberId)
        .thenCompose(listener -> listener.forceConfigure(request))
        .thenCompose(response -> transformResponse(response, ForceConfigureResponse.class))
        .orTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  @Override
  public CompletableFuture<JoinResponse> join(final MemberId memberId, final JoinRequest request) {
    intercept(request, JoinRequest.class);
    return getServer(memberId)
        .thenCompose(listener -> listener.join(request))
        .thenCompose(response -> transformResponse(response, JoinResponse.class))
        .orTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  @Override
  public CompletableFuture<LeaveResponse> leave(
      final MemberId memberId, final LeaveRequest request) {
    intercept(request, LeaveRequest.class);
    return getServer(memberId)
        .thenCompose(listener -> listener.leave(request))
        .thenCompose(response -> transformResponse(response, LeaveResponse.class))
        .orTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
>>>>>>> 281eb95c (test: verify install requests retry behavior on timeout):zeebe/atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
  }

  @Override
  public CompletableFuture<InstallResponse> install(
      final MemberId memberId, final InstallRequest request) {
<<<<<<< HEAD:atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.install(request)));
=======
    intercept(request, InstallRequest.class);
    return getServer(memberId)
        .thenCompose(listener -> listener.install(request))
        .thenCompose(response -> transformResponse(response, InstallResponse.class))
        .orTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
>>>>>>> 281eb95c (test: verify install requests retry behavior on timeout):zeebe/atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
  }

  @Override
  public CompletableFuture<TransferResponse> transfer(
      final MemberId memberId, final TransferRequest request) {
<<<<<<< HEAD:atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.transfer(request)));
=======
    intercept(request, TransferRequest.class);
    return getServer(memberId)
        .thenCompose(listener -> listener.transfer(request))
        .thenCompose(response -> transformResponse(response, TransferResponse.class))
        .orTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
>>>>>>> 281eb95c (test: verify install requests retry behavior on timeout):zeebe/atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
  }

  @Override
  public CompletableFuture<PollResponse> poll(final MemberId memberId, final PollRequest request) {
<<<<<<< HEAD:atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.poll(request)));
=======
    intercept(request, PollRequest.class);
    return getServer(memberId)
        .thenCompose(listener -> listener.poll(request))
        .thenCompose(response -> transformResponse(response, PollResponse.class))
        .orTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
>>>>>>> 281eb95c (test: verify install requests retry behavior on timeout):zeebe/atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
  }

  @Override
  public CompletableFuture<VoteResponse> vote(final MemberId memberId, final VoteRequest request) {
<<<<<<< HEAD:atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.vote(request)));
=======
    intercept(request, VoteRequest.class);
    return getServer(memberId)
        .thenCompose(listener -> listener.vote(request))
        .thenCompose(response -> transformResponse(response, VoteResponse.class))
        .orTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
>>>>>>> 281eb95c (test: verify install requests retry behavior on timeout):zeebe/atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
  }

  @Override
  public CompletableFuture<AppendResponse> append(
      final MemberId memberId, final AppendRequest request) {
<<<<<<< HEAD:atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.append(request)));
=======
    throw new IllegalArgumentException("Using old version not supported in tests");
  }

  @Override
  public CompletableFuture<AppendResponse> append(
      final MemberId memberId, final VersionedAppendRequest request) {
    intercept(request, VersionedAppendRequest.class);
    return getServer(memberId)
        .thenCompose(listener -> listener.append(request))
        .thenCompose(response -> transformResponse(response, AppendResponse.class))
        .orTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
>>>>>>> 281eb95c (test: verify install requests retry behavior on timeout):zeebe/atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
  }

  @Override
  public void registerTransferHandler(
      final Function<TransferRequest, CompletableFuture<TransferResponse>> handler) {
    transferHandler = handler;
  }

  @Override
  public void unregisterTransferHandler() {
    transferHandler = null;
  }

  @Override
  public void registerConfigureHandler(
      final Function<ConfigureRequest, CompletableFuture<ConfigureResponse>> handler) {
    configureHandler = handler;
  }

  @Override
  public void unregisterConfigureHandler() {
    configureHandler = null;
  }

  @Override
  public void registerReconfigureHandler(
      final Function<ReconfigureRequest, CompletableFuture<ReconfigureResponse>> handler) {
    reconfigureHandler = handler;
  }

  @Override
  public void unregisterReconfigureHandler() {
    reconfigureHandler = null;
  }

  @Override
  public void registerInstallHandler(
      final Function<InstallRequest, CompletableFuture<InstallResponse>> handler) {
    installHandler = handler;
  }

  @Override
  public void unregisterInstallHandler() {
    installHandler = null;
  }

  @Override
  public void registerPollHandler(
      final Function<PollRequest, CompletableFuture<PollResponse>> handler) {
    pollHandler = handler;
  }

  @Override
  public void unregisterPollHandler() {
    pollHandler = null;
  }

  @Override
  public void registerVoteHandler(
      final Function<VoteRequest, CompletableFuture<VoteResponse>> handler) {
    voteHandler = handler;
  }

  @Override
  public void unregisterVoteHandler() {
    voteHandler = null;
  }

  @Override
  public void registerAppendHandler(
      final Function<AppendRequest, CompletableFuture<AppendResponse>> handler) {
    appendHandler = handler;
  }

  @Override
  public void unregisterAppendHandler() {
    appendHandler = null;
  }

  private CompletableFuture<TestRaftServerProtocol> getServer(final MemberId memberId) {
    final TestRaftServerProtocol server = server(memberId);
    if (server != null) {
      return CompletableFuture.completedFuture(server);
    } else {
      return CompletableFuture.failedFuture(new ConnectException());
    }
  }

  CompletableFuture<AppendResponse> append(final AppendRequest request) {
    if (appendHandler != null) {
      return appendHandler.apply(request);
    } else {
      return CompletableFuture.failedFuture(new ConnectException());
    }
  }

  CompletableFuture<VoteResponse> vote(final VoteRequest request) {
    if (voteHandler != null) {
      return voteHandler.apply(request);
    } else {
      return CompletableFuture.failedFuture(new ConnectException());
    }
  }

  CompletableFuture<PollResponse> poll(final PollRequest request) {
    if (pollHandler != null) {
      return pollHandler.apply(request);
    } else {
      return CompletableFuture.failedFuture(new ConnectException());
    }
  }

  CompletableFuture<TransferResponse> transfer(final TransferRequest request) {
    if (transferHandler != null) {
      return transferHandler.apply(request);
    } else {
      return CompletableFuture.failedFuture(new ConnectException());
    }
  }

  CompletableFuture<InstallResponse> install(final InstallRequest request) {
    if (installHandler != null) {
      return installHandler.apply(request);
    } else {
      return CompletableFuture.failedFuture(new ConnectException());
    }
  }

  CompletableFuture<ReconfigureResponse> reconfigure(final ReconfigureRequest request) {
    if (reconfigureHandler != null) {
      return reconfigureHandler.apply(request);
    } else {
      return CompletableFuture.failedFuture(new ConnectException());
    }
  }

  CompletableFuture<ConfigureResponse> configure(final ConfigureRequest request) {
    if (configureHandler != null) {
      return configureHandler.apply(request);
    } else {
      return CompletableFuture.failedFuture(new ConnectException());
    }
  }
<<<<<<< HEAD:atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
=======

  public <T> void interceptRequest(final Class<T> requestType, final Consumer<T> interceptor) {
    interceptors.put(requestType, interceptor);
  }

  public <T extends RaftResponse> void interceptResponse(
      final Class<T> responseType, final ResponseInterceptor<T> interceptor) {
    responseInterceptors.put(responseType, interceptor);
  }

  @SuppressWarnings("unchecked")
  private <T> CompletableFuture<T> transformResponse(
      final T response, final Class<T> responseType) {
    final var interceptor = (ResponseInterceptor<T>) responseInterceptors.get(responseType);
    if (interceptor != null) {
      return interceptor.apply(response);
    }
    return CompletableFuture.completedFuture(response);
  }

  @SuppressWarnings("unchecked")
  private <T> void intercept(final T request, final Class<T> requestType) {
    final var interceptor = (Consumer<T>) interceptors.get(requestType);
    if (interceptor != null) {
      interceptor.accept(request);
    }
  }

  @FunctionalInterface
  public interface ResponseInterceptor<T> extends Function<T, CompletableFuture<T>> {}
>>>>>>> 281eb95c (test: verify install requests retry behavior on timeout):zeebe/atomix/cluster/src/test/java/io/atomix/raft/protocol/TestRaftServerProtocol.java
}
