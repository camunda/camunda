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
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Raft server protocol. */
public interface RaftServerProtocol {

  /**
   * Sends a configure request to the given node.
   *
   * @param memberId the node to which to send the request
   * @param request the request to send
   * @return a future to be completed with the response
   */
  CompletableFuture<ConfigureResponse> configure(MemberId memberId, ConfigureRequest request);

  /**
   * Sends a reconfigure request to the given node.
   *
   * @param memberId the node to which to send the request
   * @param request the request to send
   * @return a future to be completed with the response
   */
  CompletableFuture<ReconfigureResponse> reconfigure(MemberId memberId, ReconfigureRequest request);

  /**
   * Sends an install request to the given node.
   *
   * @param memberId the node to which to send the request
   * @param request the request to send
   * @return a future to be completed with the response
   */
  CompletableFuture<InstallResponse> install(MemberId memberId, InstallRequest request);

  /**
   * Sends a transfer request to the given node.
   *
   * @param memberId the node to which to send the request
   * @param request the request to send
   * @return a future to be completed with the response
   */
  CompletableFuture<TransferResponse> transfer(MemberId memberId, TransferRequest request);

  /**
   * Sends a poll request to the given node.
   *
   * @param memberId the node to which to send the request
   * @param request the request to send
   * @return a future to be completed with the response
   */
  CompletableFuture<PollResponse> poll(MemberId memberId, PollRequest request);

  /**
   * Sends a vote request to the given node.
   *
   * @param memberId the node to which to send the request
   * @param request the request to send
   * @return a future to be completed with the response
   */
  CompletableFuture<VoteResponse> vote(MemberId memberId, VoteRequest request);

  /**
   * Sends an append request to the given node.
   *
   * @param memberId the node to which to send the request
   * @param request the request to send
   * @return a future to be completed with the response
   */
  CompletableFuture<AppendResponse> append(MemberId memberId, AppendRequest request);

  /**
   * Registers a transfer request callback.
   *
   * @param handler the open session request handler to register
   */
  void registerTransferHandler(
      Function<TransferRequest, CompletableFuture<TransferResponse>> handler);

  /** Unregisters the transfer request handler. */
  void unregisterTransferHandler();

  /**
   * Registers a configure request callback.
   *
   * @param handler the open session request handler to register
   */
  void registerConfigureHandler(
      Function<ConfigureRequest, CompletableFuture<ConfigureResponse>> handler);

  /** Unregisters the configure request handler. */
  void unregisterConfigureHandler();

  /**
   * Registers a reconfigure request callback.
   *
   * @param handler the open session request handler to register
   */
  void registerReconfigureHandler(
      Function<ReconfigureRequest, CompletableFuture<ReconfigureResponse>> handler);

  /** Unregisters the reconfigure request handler. */
  void unregisterReconfigureHandler();

  /**
   * Registers a install request callback.
   *
   * @param handler the open session request handler to register
   */
  void registerInstallHandler(Function<InstallRequest, CompletableFuture<InstallResponse>> handler);

  /** Unregisters the install request handler. */
  void unregisterInstallHandler();

  /**
   * Registers a poll request callback.
   *
   * @param handler the open session request handler to register
   */
  void registerPollHandler(Function<PollRequest, CompletableFuture<PollResponse>> handler);

  /** Unregisters the poll request handler. */
  void unregisterPollHandler();

  /**
   * Registers a vote request callback.
   *
   * @param handler the open session request handler to register
   */
  void registerVoteHandler(Function<VoteRequest, CompletableFuture<VoteResponse>> handler);

  /** Unregisters the vote request handler. */
  void unregisterVoteHandler();

  /**
   * Registers an append request callback.
   *
   * @param handler the open session request handler to register
   */
  void registerAppendHandler(Function<AppendRequest, CompletableFuture<AppendResponse>> handler);

  /** Unregisters the append request handler. */
  void unregisterAppendHandler();
}
