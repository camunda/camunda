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
package io.atomix.raft.session.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftError;
import io.atomix.raft.protocol.CloseSessionRequest;
import io.atomix.raft.protocol.CloseSessionResponse;
import io.atomix.raft.protocol.CommandRequest;
import io.atomix.raft.protocol.CommandResponse;
import io.atomix.raft.protocol.KeepAliveRequest;
import io.atomix.raft.protocol.KeepAliveResponse;
import io.atomix.raft.protocol.MetadataRequest;
import io.atomix.raft.protocol.MetadataResponse;
import io.atomix.raft.protocol.OpenSessionRequest;
import io.atomix.raft.protocol.OpenSessionResponse;
import io.atomix.raft.protocol.QueryRequest;
import io.atomix.raft.protocol.QueryResponse;
import io.atomix.raft.protocol.RaftClientProtocol;
import io.atomix.raft.protocol.RaftRequest;
import io.atomix.raft.protocol.RaftResponse;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import java.net.ConnectException;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import org.slf4j.Logger;

/**
 * Client connection that recursively connects to servers in the cluster and attempts to submit
 * requests.
 */
public class RaftSessionConnection {

  private static final Predicate<RaftResponse> COMPLETE_PREDICATE =
      response ->
          response.status() == RaftResponse.Status.OK
              || response.error().type() == RaftError.Type.COMMAND_FAILURE
              || response.error().type() == RaftError.Type.QUERY_FAILURE
              || response.error().type() == RaftError.Type.APPLICATION_ERROR
              || response.error().type() == RaftError.Type.UNKNOWN_CLIENT
              || response.error().type() == RaftError.Type.UNKNOWN_SESSION
              || response.error().type() == RaftError.Type.UNKNOWN_SERVICE
              || response.error().type() == RaftError.Type.PROTOCOL_ERROR;

  private final Logger log;
  private final RaftClientProtocol protocol;
  private final MemberSelector selector;
  private final ThreadContext context;
  private MemberId currentNode;
  private int selectionId;

  public RaftSessionConnection(
      final RaftClientProtocol protocol,
      final MemberSelector selector,
      final ThreadContext context,
      final LoggerContext loggerContext) {
    this.protocol = checkNotNull(protocol, "protocol cannot be null");
    this.selector = checkNotNull(selector, "selector cannot be null");
    this.context = checkNotNull(context, "context cannot be null");
    this.log = ContextualLoggerFactory.getLogger(getClass(), loggerContext);
  }

  /** Resets the member selector. */
  public void reset() {
    selector.reset();
  }

  /**
   * Resets the member selector.
   *
   * @param leader the selector leader
   * @param servers the selector servers
   */
  public void reset(final MemberId leader, final Collection<MemberId> servers) {
    selector.reset(leader, servers);
  }

  /**
   * Returns the current selector leader.
   *
   * @return The current selector leader.
   */
  public MemberId leader() {
    return selector.leader();
  }

  /**
   * Returns the current set of members.
   *
   * @return The current set of members.
   */
  public Collection<MemberId> members() {
    return selector.members();
  }

  /**
   * Sends an open session request to the given node.
   *
   * @param request the request to send
   * @return a future to be completed with the response
   */
  public CompletableFuture<OpenSessionResponse> openSession(final OpenSessionRequest request) {
    final CompletableFuture<OpenSessionResponse> future = new CompletableFuture<>();
    if (context.isCurrentContext()) {
      sendRequest(request, protocol::openSession, future);
    } else {
      context.execute(() -> sendRequest(request, protocol::openSession, future));
    }
    return future;
  }

  /**
   * Sends a close session request to the given node.
   *
   * @param request the request to send
   * @return a future to be completed with the response
   */
  public CompletableFuture<CloseSessionResponse> closeSession(final CloseSessionRequest request) {
    final CompletableFuture<CloseSessionResponse> future = new CompletableFuture<>();
    if (context.isCurrentContext()) {
      sendRequest(request, protocol::closeSession, future);
    } else {
      context.execute(() -> sendRequest(request, protocol::closeSession, future));
    }
    return future;
  }

  /**
   * Sends a keep alive request to the given node.
   *
   * @param request the request to send
   * @return a future to be completed with the response
   */
  public CompletableFuture<KeepAliveResponse> keepAlive(final KeepAliveRequest request) {
    final CompletableFuture<KeepAliveResponse> future = new CompletableFuture<>();
    if (context.isCurrentContext()) {
      sendRequest(request, protocol::keepAlive, future);
    } else {
      context.execute(() -> sendRequest(request, protocol::keepAlive, future));
    }
    return future;
  }

  /**
   * Sends a query request to the given node.
   *
   * @param request the request to send
   * @return a future to be completed with the response
   */
  public CompletableFuture<QueryResponse> query(final QueryRequest request) {
    final CompletableFuture<QueryResponse> future = new CompletableFuture<>();
    if (context.isCurrentContext()) {
      sendRequest(request, protocol::query, future);
    } else {
      context.execute(() -> sendRequest(request, protocol::query, future));
    }
    return future;
  }

  /**
   * Sends a command request to the given node.
   *
   * @param request the request to send
   * @return a future to be completed with the response
   */
  public CompletableFuture<CommandResponse> command(final CommandRequest request) {
    final CompletableFuture<CommandResponse> future = new CompletableFuture<>();
    if (context.isCurrentContext()) {
      sendRequest(request, protocol::command, future);
    } else {
      context.execute(() -> sendRequest(request, protocol::command, future));
    }
    return future;
  }

  /**
   * Sends a metadata request to the given node.
   *
   * @param request the request to send
   * @return a future to be completed with the response
   */
  public CompletableFuture<MetadataResponse> metadata(final MetadataRequest request) {
    final CompletableFuture<MetadataResponse> future = new CompletableFuture<>();
    if (context.isCurrentContext()) {
      sendRequest(request, protocol::metadata, future);
    } else {
      context.execute(() -> sendRequest(request, protocol::metadata, future));
    }
    return future;
  }

  /** Sends the given request attempt to the cluster. */
  protected <T extends RaftRequest, U extends RaftResponse> void sendRequest(
      final T request,
      final BiFunction<MemberId, T, CompletableFuture<U>> sender,
      final CompletableFuture<U> future) {
    sendRequest(request, sender, 0, future);
  }

  /** Sends the given request attempt to the cluster. */
  protected <T extends RaftRequest, U extends RaftResponse> void sendRequest(
      final T request,
      final BiFunction<MemberId, T, CompletableFuture<U>> sender,
      final int count,
      final CompletableFuture<U> future) {
    final MemberId node = next();
    if (node != null) {
      log.trace("Sending {} to {}", request, node);
      final int selectionId = this.selectionId;
      sender
          .apply(node, request)
          .whenCompleteAsync(
              (r, e) -> {
                if (e != null || r != null) {
                  handleResponse(request, sender, count, selectionId, node, r, e, future);
                } else {
                  future.complete(null);
                }
              },
              context);
    } else {
      future.completeExceptionally(new ConnectException("Failed to connect to the cluster"));
    }
  }

  /** Resends a request due to a request failure, resetting the connection if necessary. */
  @SuppressWarnings("unchecked")
  protected <T extends RaftRequest> void retryRequest(
      final Throwable cause,
      final T request,
      final BiFunction sender,
      final int count,
      final int selectionId,
      final CompletableFuture future) {
    // If the connection has not changed, reset it and connect to the next server.
    if (this.selectionId == selectionId) {
      log.trace("Resetting connection. Reason: {}", cause.getMessage());
      this.currentNode = null;
    }

    // Attempt to send the request again.
    sendRequest(request, sender, count, future);
  }

  /** Handles a response from the cluster. */
  @SuppressWarnings("unchecked")
  protected <T extends RaftRequest> void handleResponse(
      final T request,
      final BiFunction sender,
      final int count,
      final int selectionId,
      final MemberId node,
      final RaftResponse response,
      Throwable error,
      final CompletableFuture future) {
    if (error == null) {
      log.trace("Received {} from {}", response, node);
      if (COMPLETE_PREDICATE.test(response)) {
        future.complete(response);
        selector.reset();
      } else {
        retryRequest(
            response.error().createException(), request, sender, count + 1, selectionId, future);
      }
    } else {
      if (error instanceof CompletionException) {
        error = error.getCause();
      }
      log.debug("{} failed! Reason: {}", request, error);
      if (error instanceof SocketException
          || error instanceof TimeoutException
          || error instanceof ClosedChannelException) {
        if (count < selector.members().size() + 1) {
          retryRequest(error, request, sender, count + 1, selectionId, future);
        } else {
          future.completeExceptionally(error);
        }
      } else {
        future.completeExceptionally(error);
      }
    }
  }

  /** Connects to the cluster. */
  protected MemberId next() {
    // If a connection was already established then use that connection.
    if (currentNode != null) {
      return currentNode;
    }

    if (!selector.hasNext()) {
      if (selector.leader() != null) {
        selector.reset(null, selector.members());
        this.currentNode = selector.next();
        this.selectionId++;
        return currentNode;
      } else {
        log.debug("Failed to connect to the cluster");
        selector.reset();
        return null;
      }
    } else {
      this.currentNode = selector.next();
      this.selectionId++;
      return currentNode;
    }
  }

  /** Closes the connection. */
  public void close() {
    selector.close();
  }
}
