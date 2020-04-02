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
package io.atomix.cluster.messaging;

import static io.atomix.utils.serializer.serializers.DefaultSerializers.BASIC;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Publish-subscribe based messaging service.
 *
 * <p>This service is an abstraction for publish-subscribe based cluster communication. Messages are
 * published and received based on arbitrary {@link String} topics. It supports several types of
 * messaging:
 *
 * <ul>
 *   <li>{@link #broadcast(String, Object)} broadcasts a message to all subscribers registered for
 *       the topic
 *   <li>{@link #unicast(String, Object)} sends a unicast message directly to one of the subscribers
 *       registered for the topic; unicast messages are generally delivered in round-robin fashion
 *   <li>{@link #send(String, Object)} sends a message directly to one of the subscribers registered
 *       for the topic and awaits a reply; direct messages are generally delivered in round-robin
 *       fashion
 * </ul>
 *
 * To register to listen for messages, use one of the {@link #subscribe(String, Consumer, Executor)}
 * methods:
 *
 * <pre>{@code
 * Subscription subscription = atomix.getEventService().subscribe("test", message -> {
 *   System.out.println("Received message");
 * }, executor).join();
 *
 * }</pre>
 *
 * To cancel the subscription for a topic, call {@link Subscription#close()} on the returned {@link
 * Subscription} object:
 *
 * <pre>{@code
 * subscription.close().join();
 *
 * }</pre>
 *
 * This API relies on {@link CompletableFuture} for asynchronous completion of all method calls.
 */
public interface ClusterEventService {

  /**
   * Broadcasts a message to all subscribers registered for the given {@code topic}.
   *
   * @param topic message topic
   * @param message message to send
   * @param <M> message type
   */
  default <M> void broadcast(final String topic, final M message) {
    broadcast(topic, message, BASIC::encode);
  }

  /**
   * Broadcasts a message to all subscribers registered for the given {@code topic}.
   *
   * @param topic message topic
   * @param message message to send
   * @param encoder function for encoding message to byte[]
   * @param <M> message type
   */
  <M> void broadcast(String topic, M message, Function<M, byte[]> encoder);

  /**
   * Unicasts a message to the next registered subscriber for {@code topic}.
   *
   * @param topic message topic
   * @param message message to send
   * @param <M> message type
   * @return future that is completed when the message is sent
   */
  default <M> CompletableFuture<Void> unicast(final String topic, final M message) {
    return unicast(topic, message, BASIC::encode);
  }

  /**
   * Unicasts a message to the next registered subscriber for {@code topic}.
   *
   * @param message message to send
   * @param topic message topic
   * @param encoder function for encoding message to byte[]
   * @param <M> message type
   * @return future that is completed when the message is sent
   */
  <M> CompletableFuture<Void> unicast(String topic, M message, Function<M, byte[]> encoder);

  /**
   * Sends a direct message to the next registered subscriber for {@code topic} and awaits a reply.
   *
   * @param topic message topic
   * @param message message to send
   * @param <M> request type
   * @param <R> reply type
   * @return reply future
   */
  default <M, R> CompletableFuture<R> send(final String topic, final M message) {
    return send(topic, message, BASIC::encode, BASIC::decode, null);
  }

  /**
   * Sends a direct message to the next registered subscriber for {@code topic} and awaits a reply.
   *
   * @param topic message topic
   * @param message message to send
   * @param timeout reply timeout
   * @param <M> request type
   * @param <R> reply type
   * @return reply future
   */
  default <M, R> CompletableFuture<R> send(
      final String topic, final M message, final Duration timeout) {
    return send(topic, message, BASIC::encode, BASIC::decode, timeout);
  }

  /**
   * Sends a direct message to the next registered subscriber for {@code topic} and awaits a reply.
   *
   * @param topic message topic
   * @param message message to send
   * @param encoder function for encoding request to byte[]
   * @param decoder function for decoding response from byte[]
   * @param <M> request type
   * @param <R> reply type
   * @return reply future
   */
  default <M, R> CompletableFuture<R> send(
      final String topic,
      final M message,
      final Function<M, byte[]> encoder,
      final Function<byte[], R> decoder) {
    return send(topic, message, encoder, decoder, null);
  }

  /**
   * Sends a direct message to the next registered subscriber for {@code topic} and awaits a reply.
   *
   * @param topic message topic
   * @param message message to send
   * @param encoder function for encoding request to byte[]
   * @param decoder function for decoding response from byte[]
   * @param timeout reply timeout
   * @param <M> request type
   * @param <R> reply type
   * @return reply future
   */
  <M, R> CompletableFuture<R> send(
      String topic,
      M message,
      Function<M, byte[]> encoder,
      Function<byte[], R> decoder,
      Duration timeout);

  /**
   * Adds a new subscriber for the specified message topic.
   *
   * @param topic message topic
   * @param handler handler function that processes the incoming message and produces a reply
   * @param executor executor to run this handler on
   * @param <M> incoming message type
   * @param <R> reply message type
   * @return future to be completed once the subscription has been propagated
   */
  default <M, R> CompletableFuture<Subscription> subscribe(
      final String topic, final Function<M, R> handler, final Executor executor) {
    return subscribe(topic, BASIC::decode, handler, BASIC::encode, executor);
  }

  /**
   * Adds a new subscriber for the specified message topic.
   *
   * @param topic message topic
   * @param decoder decoder for resurrecting incoming message
   * @param handler handler function that processes the incoming message and produces a reply
   * @param encoder encoder for serializing reply
   * @param executor executor to run this handler on
   * @param <M> incoming message type
   * @param <R> reply message type
   * @return future to be completed once the subscription has been propagated
   */
  <M, R> CompletableFuture<Subscription> subscribe(
      String topic,
      Function<byte[], M> decoder,
      Function<M, R> handler,
      Function<R, byte[]> encoder,
      Executor executor);

  /**
   * Adds a new subscriber for the specified message topic.
   *
   * @param topic message topic
   * @param handler handler function that processes the incoming message and produces a reply
   * @param <M> incoming message type
   * @param <R> reply message type
   * @return future to be completed once the subscription has been propagated
   */
  default <M, R> CompletableFuture<Subscription> subscribe(
      final String topic, final Function<M, CompletableFuture<R>> handler) {
    return subscribe(topic, BASIC::decode, handler, BASIC::encode);
  }

  /**
   * Adds a new subscriber for the specified message topic.
   *
   * @param topic message topic
   * @param decoder decoder for resurrecting incoming message
   * @param handler handler function that processes the incoming message and produces a reply
   * @param encoder encoder for serializing reply
   * @param <M> incoming message type
   * @param <R> reply message type
   * @return future to be completed once the subscription has been propagated
   */
  <M, R> CompletableFuture<Subscription> subscribe(
      String topic,
      Function<byte[], M> decoder,
      Function<M, CompletableFuture<R>> handler,
      Function<R, byte[]> encoder);

  /**
   * Adds a new subscriber for the specified message topic.
   *
   * @param topic message topic
   * @param handler handler for handling message
   * @param executor executor to run this handler on
   * @param <M> incoming message type
   * @return future to be completed once the subscription has been propagated
   */
  default <M> CompletableFuture<Subscription> subscribe(
      final String topic, final Consumer<M> handler, final Executor executor) {
    return subscribe(topic, BASIC::decode, handler, executor);
  }

  /**
   * Adds a new subscriber for the specified message topic.
   *
   * @param topic message topic
   * @param decoder decoder to resurrecting incoming message
   * @param handler handler for handling message
   * @param executor executor to run this handler on
   * @param <M> incoming message type
   * @return future to be completed once the subscription has been propagated
   */
  <M> CompletableFuture<Subscription> subscribe(
      String topic, Function<byte[], M> decoder, Consumer<M> handler, Executor executor);

  /**
   * Returns a list of subscriptions for the given topic.
   *
   * @param topic the topic for which to return subscriptions
   * @return the subscriptions for the given topic
   */
  List<Subscription> getSubscriptions(String topic);
}
