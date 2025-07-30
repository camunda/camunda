/*
 * Copyright 2015-present Open Networking Foundation
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

import io.atomix.utils.net.Address;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/** Interface for low level messaging primitives. */
public interface MessagingService {

  /**
   * Returns the local messaging service address. This is the address used by other nodes to
   * communicate to this service.
   *
   * @return the address remote nodes use to communicate to this node
   */
  Address address();

  /**
   * Returns the interfaces to which the local messaging service is bind.
   *
   * @return the address the messaging service is bound to
   */
  Collection<Address> bindingAddresses();

  /**
   * Sends a message asynchronously to the specified communication address. The message is specified
   * using the type and payload.
   *
   * <p>The future may be completed exceptionally with one of the following:
   *
   * <ul>
   *   <li>{@link java.net.ConnectException} - indicates the recipient is unreachable
   *   <li>{@link java.util.concurrent.TimeoutException} - indicates no response came back within
   *       the given timeout
   * </ul>
   *
   * @param address address to send the message to.
   * @param type type of message.
   * @param payload message payload bytes.
   * @return future that is completed when the message is sent
   */
  default CompletableFuture<Void> sendAsync(
      final Address address, final String type, final byte[] payload) {
    return sendAsync(address, type, payload, true);
  }

  /**
   * Sends a message asynchronously to the specified communication address. The message is specified
   * using the type and payload.
   *
   * @param address address to send the message to.
   * @param type type of message.
   * @param payload message payload bytes.
   * @param keepAlive whether to keep the connection alive after usage
   * @return future that is completed when the message is sent
   */
  default CompletableFuture<Void> sendAsync(
      final Address address, final String type, final byte[] payload, final boolean keepAlive) {
    return sendAsync(address, type, payload, keepAlive, false);
  }

  /**
   * Sends a message asynchronously to the specified communication address. The message is specified
   * using the type and payload.
   *
   * @param address address to send the message to.
   * @param type type of message.
   * @param payload message payload bytes.
   * @param keepAlive whether to keep the connection alive after usage
   * @return future that is completed when the message is sent
   */
  CompletableFuture<Void> sendAsync(
      Address address,
      String type,
      byte[] payload,
      boolean keepAlive,
      final boolean dedicatedChannel);

  /**
   * Sends a message asynchronously and expects a response on a pooled connection.
   *
   * <p>The future may be completed exceptionally with one of:
   *
   * <ul>
   *   <li>{@link IllegalStateException} - the underlying service cannot send the given request
   *       (e.g. the service is not running)
   *   <li>{@link java.net.ConnectException} - indicates the recipient is unreachable
   *   <li>{@link java.util.concurrent.TimeoutException} - indicates no response came back after the
   *       default timeout
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.ProtocolException} - indicates the
   *       recipient failed to parse the incoming message
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.NoRemoteHandler} - indicates the
   *       recipient received the message, but was no expecting to
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.RemoteHandlerFailure} - indicates
   *       the recipient parsed and expected the message, but it failed unexpectedly to process it
   * </ul>
   *
   * @param address address to send the message to.
   * @param type type of message.
   * @param payload message payload.
   * @return a response future
   */
  default CompletableFuture<byte[]> sendAndReceive(
      final Address address, final String type, final byte[] payload) {
    return sendAndReceive(address, type, payload, true);
  }

  /**
   * Sends a message asynchronously and expects a response.
   *
   * <p>If {@code keepAlive} is false, a new, transient connection is set up and created. If true,
   * it will reuse an existing connection (if any), or create a new one that will be kept in a pool
   * for future reuse for this address and type pair.
   *
   * <p>The future may be completed exceptionally with one of:
   *
   * <ul>
   *   <li>{@link IllegalStateException} - the underlying service cannot send the given request
   *       (e.g. the service is not running)
   *   <li>{@link java.net.ConnectException} - indicates the recipient is unreachable
   *   <li>{@link java.util.concurrent.TimeoutException} - indicates no response came back after the
   *       default timeout
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.ProtocolException} - indicates the
   *       recipient failed to parse the incoming message
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.NoRemoteHandler} - indicates the
   *       recipient received the message, but was no expecting to
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.RemoteHandlerFailure} - indicates
   *       the recipient parsed and expected the message, but it failed unexpectedly to process it
   * </ul>
   *
   * @param address address to send the message to.
   * @param type type of message.
   * @param payload message payload.
   * @param keepAlive whether to keep the connection alive after usage
   * @return a response future
   */
  CompletableFuture<byte[]> sendAndReceive(
      Address address, String type, byte[] payload, boolean keepAlive);

  /**
   * Sends a message synchronously and expects a response on a pooled connection.
   *
   * <p>The future may be completed exceptionally with one of:
   *
   * <ul>
   *   <li>{@link IllegalStateException} - the underlying service cannot send the given request
   *       (e.g. the service is not running)
   *   <li>{@link java.net.ConnectException} - indicates the recipient is unreachable
   *   <li>{@link java.util.concurrent.TimeoutException} - indicates no response came back after the
   *       default timeout
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.ProtocolException} - indicates the
   *       recipient failed to parse the incoming message
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.NoRemoteHandler} - indicates the
   *       recipient received the message, but was no expecting to
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.RemoteHandlerFailure} - indicates
   *       the recipient parsed and expected the message, but it failed unexpectedly to process it
   * </ul>
   *
   * @param address address to send the message to.
   * @param type type of message.
   * @param payload message payload.
   * @param executor executor over which any follow up actions after completion will be executed.
   * @return a response future
   */
  default CompletableFuture<byte[]> sendAndReceive(
      final Address address, final String type, final byte[] payload, final Executor executor) {
    return sendAndReceive(address, type, payload, true, executor);
  }

  /**
   * Sends a message synchronously and expects a response.
   *
   * <p>If {@code keepAlive} is false, a new, transient connection is set up and created. If true,
   * it will reuse an existing connection (if any), or create a new one that will be kept in a pool
   * for future reuse for this address and type pair.
   *
   * <p>The future may be completed exceptionally with one of:
   *
   * <ul>
   *   <li>{@link IllegalStateException} - the underlying service cannot send the given request
   *       (e.g. the service is not running)
   *   <li>{@link java.net.ConnectException} - indicates the recipient is unreachable
   *   <li>{@link java.util.concurrent.TimeoutException} - indicates no response came back after the
   *       default timeout
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.ProtocolException} - indicates the
   *       recipient failed to parse the incoming message
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.NoRemoteHandler} - indicates the
   *       recipient received the message, but was no expecting to
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.RemoteHandlerFailure} - indicates
   *       the recipient parsed and expected the message, but it failed unexpectedly to process it
   * </ul>
   *
   * @param address address to send the message to.
   * @param type type of message.
   * @param payload message payload.
   * @param keepAlive whether to keep the connection alive after usage
   * @param executor executor over which any follow up actions after completion will be executed.
   * @return a response future
   */
  CompletableFuture<byte[]> sendAndReceive(
      Address address, String type, byte[] payload, boolean keepAlive, Executor executor);

  /**
   * Sends a message asynchronously and expects a response on a pooled connection.
   *
   * <p>The future may be completed exceptionally with one of:
   *
   * <ul>
   *   <li>{@link IllegalStateException} - the underlying service cannot send the given request
   *       (e.g. the service is not running)
   *   <li>{@link java.net.ConnectException} - indicates the recipient is unreachable
   *   <li>{@link java.util.concurrent.TimeoutException} - indicates no response came back within
   *       the given timeout
   *   <li>{@link MessagingException.ProtocolException} - indicates the recipient failed to parse
   *       the incoming message
   *   <li>{@link MessagingException.NoRemoteHandler} - indicates the recipient received the
   *       message, but was no expecting to
   *   <li>{@link MessagingException.RemoteHandlerFailure} - indicates the recipient parsed and
   *       expected the message, but it failed unexpectedly to process it
   * </ul>
   *
   * @param address address to send the message to.
   * @param type type of message.
   * @param payload message payload.
   * @param timeout response timeout
   * @return a response future
   */
  default CompletableFuture<byte[]> sendAndReceive(
      final Address address, final String type, final byte[] payload, final Duration timeout) {
    return sendAndReceive(address, type, false, payload, timeout);
  }

  /**
   * Sends a message asynchronously and expects a response on a pooled connection.
   *
   * <p>The future may be completed exceptionally with one of:
   *
   * <ul>
   *   <li>{@link IllegalStateException} - the underlying service cannot send the given request
   *       (e.g. the service is not running)
   *   <li>{@link java.net.ConnectException} - indicates the recipient is unreachable
   *   <li>{@link java.util.concurrent.TimeoutException} - indicates no response came back within
   *       the given timeout
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.ProtocolException} - indicates the
   *       recipient failed to parse the incoming message
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.NoRemoteHandler} - indicates the
   *       recipient received the message, but was no expecting to
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.RemoteHandlerFailure} - indicates
   *       the recipient parsed and expected the message, but it failed unexpectedly to process it
   * </ul>
   *
   * @param address address to send the message to.
   * @param type type of message.
   * @param payload message payload.
   * @param timeout response timeout
   * @return a response future
   */
  default CompletableFuture<byte[]> sendAndReceive(
      final Address address,
      final String type,
      final boolean dedicatedConnection,
      final byte[] payload,
      final Duration timeout) {
    return sendAndReceive(address, type, dedicatedConnection, payload, true, timeout);
  }

  /**
   * Sends a message asynchronously and expects a response.
   *
   * <p>If {@code keepAlive} is false, a new, transient connection is set up and created. If true,
   * it will reuse an existing connection (if any), or create a new one that will be kept in a pool
   * for future reuse for this address and type pair.
   *
   * <p>The future may be completed exceptionally with one of:
   *
   * <ul>
   *   <li>{@link IllegalStateException} - the underlying service cannot send the given request
   *       (e.g. the service is not running)
   *   <li>{@link java.net.ConnectException} - indicates the recipient is unreachable
   *   <li>{@link java.util.concurrent.TimeoutException} - indicates no response came back within
   *       the given timeout
   *   <li>{@link MessagingException.ProtocolException} - indicates the recipient failed to parse
   *       the incoming message
   *   <li>{@link MessagingException.NoRemoteHandler} - indicates the recipient received the
   *       message, but was no expecting to
   *   <li>{@link MessagingException.RemoteHandlerFailure} - indicates the recipient parsed and
   *       expected the message, but it failed unexpectedly to process it
   * </ul>
   *
   * @param address address to send the message to.
   * @param type type of message.
   * @param payload message payload.
   * @param keepAlive whether to keep the connection alive after usage
   * @param timeout response timeout
   * @return a response future
   */
  default CompletableFuture<byte[]> sendAndReceive(
      final Address address,
      final String type,
      final byte[] payload,
      final boolean keepAlive,
      final Duration timeout) {
    return sendAndReceive(address, type, false, payload, keepAlive, timeout);
  }

  /**
   * Sends a message asynchronously and expects a response.
   *
   * <p>If {@code keepAlive} is false, a new, transient connection is set up and created. If true,
   * it will reuse an existing connection (if any), or create a new one that will be kept in a pool
   * for future reuse for this address and type pair.
   *
   * <p>The future may be completed exceptionally with one of:
   *
   * <ul>
   *   <li>{@link IllegalStateException} - the underlying service cannot send the given request
   *       (e.g. the service is not running)
   *   <li>{@link java.net.ConnectException} - indicates the recipient is unreachable
   *   <li>{@link java.util.concurrent.TimeoutException} - indicates no response came back within
   *       the given timeout
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.ProtocolException} - indicates the
   *       recipient failed to parse the incoming message
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.NoRemoteHandler} - indicates the
   *       recipient received the message, but was no expecting to
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.RemoteHandlerFailure} - indicates
   *       the recipient parsed and expected the message, but it failed unexpectedly to process it
   * </ul>
   *
   * @param address address to send the message to.
   * @param type type of message.
   * @param payload message payload.
   * @param keepAlive whether to keep the connection alive after usage
   * @param timeout response timeout
   * @return a response future
   */
  CompletableFuture<byte[]> sendAndReceive(
      Address address,
      String type,
      final boolean dedicatedChannel,
      byte[] payload,
      boolean keepAlive,
      Duration timeout);

  /**
   * Sends a message synchronously and expects a response.
   *
   * <p>If {@code keepAlive} is false, a new, transient connection is set up and created. If true,
   * it will reuse an existing connection (if any), or create a new one that will be kept in a pool
   * for future reuse for this address and type pair.
   *
   * <p>The future may be completed exceptionally with one of:
   *
   * <ul>
   *   <li>{@link IllegalStateException} - the underlying service cannot send the given request
   *       (e.g. the service is not running)
   *   <li>{@link java.net.ConnectException} - indicates the recipient is unreachable
   *   <li>{@link java.util.concurrent.TimeoutException} - indicates no response came back within
   *       the given timeout
   *   <li>{@link MessagingException.ProtocolException} - indicates the recipient failed to parse
   *       the incoming message
   *   <li>{@link MessagingException.NoRemoteHandler} - indicates the recipient received the
   *       message, but was no expecting to
   *   <li>{@link MessagingException.RemoteHandlerFailure} - indicates the recipient parsed and
   *       expected the message, but it failed unexpectedly to process it
   * </ul>
   *
   * @param address address to send the message to.
   * @param type type of message.
   * @param payload message payload.
   * @param keepAlive whether to keep the connection alive after usage
   * @param timeout response timeout
   * @param executor executor over which any follow up actions after completion will be executed.
   * @return a response future
   */
  default CompletableFuture<byte[]> sendAndReceive(
      final Address address,
      final String type,
      final byte[] payload,
      final boolean keepAlive,
      final Duration timeout,
      final Executor executor) {
    return sendAndReceive(address, type, false, payload, keepAlive, timeout, executor);
  }

  /**
   * Sends a message synchronously and expects a response.
   *
   * <p>If {@code keepAlive} is false, a new, transient connection is set up and created. If true,
   * it will reuse an existing connection (if any), or create a new one that will be kept in a pool
   * for future reuse for this address and type pair.
   *
   * <p>The future may be completed exceptionally with one of:
   *
   * <ul>
   *   <li>{@link IllegalStateException} - the underlying service cannot send the given request
   *       (e.g. the service is not running)
   *   <li>{@link java.net.ConnectException} - indicates the recipient is unreachable
   *   <li>{@link java.util.concurrent.TimeoutException} - indicates no response came back within
   *       the given timeout
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.ProtocolException} - indicates the
   *       recipient failed to parse the incoming message
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.NoRemoteHandler} - indicates the
   *       recipient received the message, but was no expecting to
   *   <li>{@link io.atomix.cluster.messaging.MessagingException.RemoteHandlerFailure} - indicates
   *       the recipient parsed and expected the message, but it failed unexpectedly to process it
   * </ul>
   *
   * @param address address to send the message to.
   * @param type type of message.
   * @param payload message payload.
   * @param keepAlive whether to keep the connection alive after usage
   * @param timeout response timeout
   * @param executor executor over which any follow up actions after completion will be executed.
   * @return a response future
   */
  CompletableFuture<byte[]> sendAndReceive(
      Address address,
      String type,
      final boolean dedicatedChannel,
      byte[] payload,
      boolean keepAlive,
      Duration timeout,
      Executor executor);

  /**
   * Registers a new message handler for message type.
   *
   * @param type message type.
   * @param handler message handler
   * @param executor executor to use for running message handler logic.
   */
  void registerHandler(String type, BiConsumer<Address, byte[]> handler, Executor executor);

  /**
   * Registers a new message handler for message type.
   *
   * @param type message type.
   * @param handler message handler
   * @param executor executor to use for running message handler logic.
   */
  void registerHandler(String type, BiFunction<Address, byte[], byte[]> handler, Executor executor);

  /**
   * Registers a new message handler for message type.
   *
   * @param type message type.
   * @param handler message handler
   */
  void registerHandler(String type, BiFunction<Address, byte[], CompletableFuture<byte[]>> handler);

  /**
   * Unregister current handler, if one exists for message type.
   *
   * @param type message type
   */
  void unregisterHandler(String type);

  /**
   * Returns a boolean value indicating whether the managed object is running.
   *
   * @return Indicates whether the managed object is running.
   */
  boolean isRunning();
}
