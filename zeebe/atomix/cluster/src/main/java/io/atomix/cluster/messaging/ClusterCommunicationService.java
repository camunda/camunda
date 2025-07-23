/*
 * Copyright 2014-present Open Networking Foundation
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
package io.atomix.cluster.messaging;

import io.atomix.cluster.MemberId;
import io.atomix.utils.net.Address;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * High-level {@link MemberId} based intra-cluster messaging service.
 *
 * <p>The cluster communication service is used for high-level communication between cluster
 * members. Messages are sent and received based on arbitrary {@link String} message subjects.
 * Direct messages are sent using the {@link MemberId} to which to send the message. This API
 * supports several types of messaging:
 *
 * <ul>
 *   <li>{@link #broadcast(String, Object, Function, boolean)} broadcasts a message to all cluster
 *       members
 *   <li>{@link #multicast(String, Object, Function, Set, boolean)} sends the message to all
 *       provided members
 *   <li>{@link #unicast(String, Object, Function, MemberId, boolean)} sends a unicast message
 *       directly to the given member
 *   <li>{@link #send(String, Object, Function, Function, MemberId, Duration)} sends a message
 *       directly to the given member and awaits a reply
 * </ul>
 *
 * To register to listen for messages, use one of the {@link #consume(String, Function, Consumer,
 * Executor)} methods:
 *
 * <pre>{@code
 * service.consume("test", String::new, message -> {
 *   System.out.println("Received message " + message);
 * }, executor);
 *
 * }</pre>
 */
public interface ClusterCommunicationService {

  /**
   * Broadcasts a message to all members.
   *
   * @param subject message subject
   * @param message message to send
   * @param encoder function for encoding message to byte[]
   * @param reliable whether to perform a reliable (TCP) unicast or not (UDP)
   * @param <M> message type
   */
  <M> void broadcast(String subject, M message, Function<M, byte[]> encoder, boolean reliable);

  /**
   * Multicasts a message to a set of members.
   *
   * @param subject message subject
   * @param message message to send
   * @param encoder function for encoding message to byte[]
   * @param memberIds recipient node identifiers
   * @param reliable whether to perform a reliable (TCP) unicast or not (UDP)
   * @param <M> message type
   */
  <M> void multicast(
      String subject,
      M message,
      Function<M, byte[]> encoder,
      Set<MemberId> memberIds,
      boolean reliable);

  /**
   * Sends a message to a member.
   *
   * @param subject message subject
   * @param message message to send
   * @param encoder function for encoding message to byte[]
   * @param memberId recipient node identifier
   * @param reliable whether to perform a reliable (TCP) unicast or not (UDP)
   * @param <M> message type
   */
  <M> void unicast(
      String subject, M message, Function<M, byte[]> encoder, MemberId memberId, boolean reliable);

  /**
   * Sends a message and expects a reply.
   *
   * <p>The returned future may be completed exceptionally with any exceptions listed by {@link
   * MessagingService#sendAndReceive(Address, String, byte[], boolean, Duration, Executor)}, as well
   * as:
   *
   * <ul>
   *   <li>{@link MessagingException.NoSuchMemberException} - indicates that the local membership
   *       protocol cannot resolve the given member ID to a node address
   * </ul>
   *
   * @param subject message subject
   * @param message message to send
   * @param encoder function for encoding request to byte[]
   * @param decoder function for decoding response from byte[]
   * @param toMemberId recipient node identifier
   * @param timeout response timeout
   * @param <M> request type
   * @param <R> reply type
   * @return reply future
   */
  default <M, R> CompletableFuture<R> send(
      final String subject,
      final M message,
      final Function<M, byte[]> encoder,
      final Function<byte[], R> decoder,
      final MemberId toMemberId,
      final Duration timeout) {
    return send(subject, message, encoder, decoder, toMemberId, timeout, false);
  }

  /**
   * Sends a message and expects a reply.
   *
   * <p>The returned future may be completed exceptionally with any exceptions listed by {@link
   * MessagingService#sendAndReceive(Address, String, byte[], boolean, Duration, Executor)}, as well
   * as:
   *
   * <ul>
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.NoSuchMemberException} - indicates
   *       that the local membership protocol cannot resolve the given member ID to a node address
   * </ul>
   *
   * @param <M> request type
   * @param <R> reply type
   * @param subject message subject
   * @param message message to send
   * @param encoder function for encoding request to byte[]
   * @param decoder function for decoding response from byte[]
   * @param toMemberId recipient node identifier
   * @param timeout response timeout
   * @return reply future
   */
  <M, R> CompletableFuture<R> send(
      String subject,
      M message,
      Function<M, byte[]> encoder,
      Function<byte[], R> decoder,
      MemberId toMemberId,
      Duration timeout,
      final boolean dedicatedConnection);

  /**
   * Adds a new subscriber for the specified message subject, which must return a reply.
   *
   * @param subject message subject
   * @param decoder decoder for deserialize incoming message
   * @param handler handler function that processes the incoming message and produces a reply
   * @param encoder encoder for serializing reply
   * @param <M> incoming message type
   * @param <R> reply message type
   */
  <M, R> void replyTo(
      String subject,
      Function<byte[], M> decoder,
      Function<M, CompletableFuture<R>> handler,
      Function<R, byte[]> encoder);

  /**
   * Adds a new subscriber for the specified message subject which does not return any reply.
   *
   * @param subject message subject
   * @param decoder decoder to deserialize incoming message
   * @param handler handler for handling message
   * @param executor executor to run this handler on
   * @param <M> incoming message type
   */
  <M> void consume(
      String subject, Function<byte[], M> decoder, Consumer<M> handler, Executor executor);

  /**
   * Adds a new subscriber for the specified message subject which does not return any reply. If the
   * sender is not a known member, the handler is not called (but no error is returned to the
   * sender).
   *
   * @param subject message subject
   * @param decoder decoder to deserialize incoming message
   * @param handler handler for handling message, receiving the sender's member ID and the decoded
   *     message
   * @param executor executor to run this handler on
   * @param <M> incoming message type
   */
  <M> void consume(
      String subject,
      Function<byte[], M> decoder,
      BiConsumer<MemberId, M> handler,
      Executor executor);

  /**
   * Adds a new subscriber for the specified message subject which must return a reply. If the
   * sender is not a known member, the handler is not called, and a {@link
   * io.atomix.cluster.messaging.MessagingException.NoSuchMemberException} is returned to the
   * sender.
   *
   * @param subject message subject
   * @param decoder decoder to deserializing incoming message
   * @param handler handler for handling message, receiving the sender's member ID and the decoded
   *     message
   * @param encoder to serialize the outgoing reply
   * @param executor executor to run this handler on
   * @param <M> incoming message type
   */
  <M, R> void replyTo(
      String subject,
      Function<byte[], M> decoder,
      BiFunction<MemberId, M, R> handler,
      Function<R, byte[]> encoder,
      Executor executor);

  /**
   * Adds a new subscriber for the specified message subject which must return a reply. If the
   * sender is not a known member, the handler is not called, and a {@link
   * io.atomix.cluster.messaging.MessagingException.NoSuchMemberException} is returned to the
   * sender.
   *
   * @param subject message subject
   * @param decoder decoder to deserializing incoming message
   * @param handler handler receives the decoded message and returns a future which is completed
   *     with the reply (which will be encoded using the given encoder)
   * @param encoder to serialize the outgoing reply
   * @param executor executor to run this handler on
   * @param <M> incoming message type
   */
  <M, R> void replyToAsync(
      String subject,
      Function<byte[], M> decoder,
      Function<M, CompletableFuture<R>> handler,
      Function<R, byte[]> encoder,
      Executor executor);

  /**
   * Removes a subscriber for the specified message subject.
   *
   * @param subject message subject
   */
  void unsubscribe(String subject);
}
