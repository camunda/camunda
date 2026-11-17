/*
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
package io.atomix.raft.impl;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftError;
import io.atomix.raft.RaftException;
import io.atomix.raft.protocol.AbstractRaftResponse;
import io.atomix.raft.protocol.RaftRequest;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.RaftServerProtocol;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Wraps the protocol that the local member uses to send requests, so that fencing is applied in one
 * place instead of at every request and response handler:
 *
 * <ul>
 *   <li>every request that the local member sends carries its incarnation;
 *   <li>a {@link RaftError.Type#FENCED} rejection tells the local member that it is fenced;
 *   <li>a response from a fenced member fails like an unreachable member, so that its term is never
 *       taken over.
 * </ul>
 */
final class OutboundRaftProtocol {

  private OutboundRaftProtocol() {}

  static RaftServerProtocol wrap(final RaftServerProtocol delegate, final RaftContext raft) {
    return (RaftServerProtocol)
        Proxy.newProxyInstance(
            RaftServerProtocol.class.getClassLoader(),
            new Class<?>[] {RaftServerProtocol.class},
            (proxy, method, args) -> {
              if (args != null
                  && args.length == 2
                  && args[0] instanceof final MemberId target
                  && args[1] instanceof final RaftRequest request) {
                stamp(request, raft);
                final CompletableFuture<?> response =
                    (CompletableFuture<?>) invoke(delegate, method, args);
                return response.thenApply(r -> checkResponse(target, r, raft));
              }
              return invoke(delegate, method, args);
            });
  }

  private static void stamp(final RaftRequest request, final RaftContext raft) {
    final var localMember = raft.getCluster().getLocalMember();
    // A forwarded request keeps the incarnation of its original sender.
    if (localMember.memberId().equals(request.from())) {
      request.setSenderIncarnation(localMember.getIncarnation());
    }
  }

  private static <T> T checkResponse(
      final MemberId target, final T response, final RaftContext raft) {
    if (!(response instanceof final AbstractRaftResponse raftResponse)) {
      return response;
    }
    if (raftResponse.status() == Status.ERROR
        && raftResponse.error() != null
        && raftResponse.error().type() == RaftError.Type.FENCED) {
      raft.getThreadContext()
          .execute(
              () ->
                  raft.onFenced(
                      "%s rejected a request: %s"
                          .formatted(target, raftResponse.error().message())));
      return response;
    }
    final var configuration = raft.getCluster().getConfiguration();
    if (configuration != null && configuration.isFenced(target, raftResponse.senderIncarnation())) {
      throw new CompletionException(
          new RaftException.ProtocolException(
              "Ignoring the response of %s, its incarnation %d is fenced",
              target, raftResponse.senderIncarnation()));
    }
    return response;
  }

  private static Object invoke(
      final RaftServerProtocol delegate, final Method method, final Object[] args)
      throws Throwable {
    try {
      return method.invoke(delegate, args);
    } catch (final InvocationTargetException e) {
      throw e.getCause();
    }
  }
}
