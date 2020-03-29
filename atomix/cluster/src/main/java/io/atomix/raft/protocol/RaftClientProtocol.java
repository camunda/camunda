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

import io.atomix.cluster.MemberId;
import io.atomix.primitive.session.SessionId;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/** Raft client protocol. */
public interface RaftClientProtocol {

  /**
   * Sends an open session request to the given node.
   *
   * @param memberId the node to which to send the request
   * @param request the request to send
   * @return a future to be completed with the response
   */
  CompletableFuture<OpenSessionResponse> openSession(MemberId memberId, OpenSessionRequest request);

  /**
   * Sends a close session request to the given node.
   *
   * @param memberId the node to which to send the request
   * @param request the request to send
   * @return a future to be completed with the response
   */
  CompletableFuture<CloseSessionResponse> closeSession(
      MemberId memberId, CloseSessionRequest request);

  /**
   * Sends a keep alive request to the given node.
   *
   * @param memberId the node to which to send the request
   * @param request the request to send
   * @return a future to be completed with the response
   */
  CompletableFuture<KeepAliveResponse> keepAlive(MemberId memberId, KeepAliveRequest request);

  /**
   * Sends a query request to the given node.
   *
   * @param memberId the node to which to send the request
   * @param request the request to send
   * @return a future to be completed with the response
   */
  CompletableFuture<QueryResponse> query(MemberId memberId, QueryRequest request);

  /**
   * Sends a command request to the given node.
   *
   * @param memberId the node to which to send the request
   * @param request the request to send
   * @return a future to be completed with the response
   */
  CompletableFuture<CommandResponse> command(MemberId memberId, CommandRequest request);

  /**
   * Sends a metadata request to the given node.
   *
   * @param memberId the node to which to send the request
   * @param request the request to send
   * @return a future to be completed with the response
   */
  CompletableFuture<MetadataResponse> metadata(MemberId memberId, MetadataRequest request);

  /**
   * Multicasts a reset request to all nodes in the cluster.
   *
   * @param members the members to which to send the request
   * @param request the reset request to multicast
   */
  void reset(Set<MemberId> members, ResetRequest request);

  /**
   * Registers a heartbeat request callback.
   *
   * @param handler the heartbeat request handler to register
   */
  void registerHeartbeatHandler(
      Function<HeartbeatRequest, CompletableFuture<HeartbeatResponse>> handler);

  /** Unregisters the heartbeat request handler. */
  void unregisterHeartbeatHandler();

  /**
   * Registers a publish request listener.
   *
   * @param sessionId the session for which to listen for the publish request
   * @param listener the listener to register
   * @param executor the executor with which to execute the listener callback
   */
  void registerPublishListener(
      SessionId sessionId, Consumer<PublishRequest> listener, Executor executor);

  /**
   * Unregisters the publish request listener for the given session.
   *
   * @param sessionId the session for which to unregister the listener
   */
  void unregisterPublishListener(SessionId sessionId);
}
