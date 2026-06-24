/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.cluster.messaging.impl;

import com.google.common.collect.Maps;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.util.collection.Tuple;
import io.netty.channel.Channel;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Internal Netty channel pool. */
class ChannelPool {
  private static final Logger LOGGER = LoggerFactory.getLogger(ChannelPool.class);

  private final Function<Address, CompletableFuture<Channel>> factory;
  private final Map<Tuple<Address, InetAddress>, Map<String, CompletableFuture<Channel>>> channels =
      Maps.newConcurrentMap();

  ChannelPool(final Function<Address, CompletableFuture<Channel>> factory) {
    this.factory = factory;
  }

  /**
   * Returns the channel pool for the given address.
   *
   * @param address the address for which to return the channel pool
   * @return the channel pool for the given address
   */
  private Map<String, CompletableFuture<Channel>> getChannelPool(
      final Address address, final InetAddress inetAddress) {
    return channels.computeIfAbsent(
        new Tuple<>(address, inetAddress), k -> Maps.newConcurrentMap());
  }

  /**
   * Gets or creates a pooled channel to the given address for the given message type.
   *
   * @param address the address for which to get the channel
   * @param messageType the message type for which to get the channel
   * @return a future to be completed with a channel from the pool
   */
  CompletableFuture<Channel> getChannel(final Address address, final String messageType) {
    final InetAddress inetAddress = address.getAddress();

    final Map<String, CompletableFuture<Channel>> channelPool =
        getChannelPool(address, inetAddress);

    CompletableFuture<Channel> channelFuture = channelPool.get(messageType);
    if (channelFuture == null || channelFuture.isCompletedExceptionally()) {
      synchronized (channelPool) {
        channelFuture = channelPool.get(messageType);
        if (channelFuture == null || channelFuture.isCompletedExceptionally()) {
          LOGGER.debug("Connecting to {}", address);
          channelFuture = factory.apply(address);
          final var finalFuture = channelFuture;
          channelFuture.whenComplete(
              (channel, error) -> {
                if (error == null) {
                  LOGGER.debug("Connected to {}", channel.remoteAddress());
                  // Remove channel from the pool when it is closed
                  channel
                      .closeFuture()
                      .addListener(
                          closed -> {
                            synchronized (channelPool) {
                              // Remove channel from the pool after it is closed.
                              removeChannel(channelPool, messageType, finalFuture);
                            }
                          });
                } else {
                  LOGGER.debug("Failed to connect to {}", address, error);
                }
              });
          channelPool.put(messageType, channelFuture);
        }
      }
    }

    final CompletableFuture<Channel> future = new CompletableFuture<>();
    final CompletableFuture<Channel> finalFuture = channelFuture;
    finalFuture.whenComplete(
        (channel, error) -> {
          if (error == null) {
            if (!channel.isActive()) {
              CompletableFuture<Channel> currentFuture;
              synchronized (channelPool) {
                currentFuture = channelPool.get(messageType);
                if (currentFuture == finalFuture) {
                  channelPool.put(messageType, null);
                } else if (currentFuture == null) {
                  currentFuture = factory.apply(address);
                  currentFuture.whenComplete(this::logConnection);
                  channelPool.put(messageType, currentFuture);
                }
              }

              if (currentFuture == finalFuture) {
                getChannel(address, messageType)
                    .whenComplete(
                        (recursiveResult, recursiveError) -> {
                          completeFuture(future, recursiveResult, recursiveError);
                        });
              } else {
                // LGTM false positive https://github.com/Semmle/ql/issues/3176
                currentFuture.whenComplete( // lgtm [java/dereferenced-value-may-be-null]
                    (recursiveResult, recursiveError) -> {
                      completeFuture(future, recursiveResult, recursiveError);
                    });
              }
            } else {
              future.complete(channel);
            }
          } else {
            future.completeExceptionally(error);
          }
        });
    return future;
  }

  private static void removeChannel(
      final Map<String, CompletableFuture<Channel>> channelPool,
      final String messageType,
      final CompletableFuture<Channel> finalFuture) {
    final var currentFuture = channelPool.get(messageType);
    // check if new channel is already replaced before removing it.
    if (finalFuture == currentFuture) {
      channelPool.remove(messageType);
    }
  }

  private void completeFuture(
      final CompletableFuture<Channel> future,
      final Channel recursiveResult,
      final Throwable recursiveError) {
    if (recursiveError == null) {
      future.complete(recursiveResult);
    } else {
      future.completeExceptionally(recursiveError);
    }
  }

  private void logConnection(final Channel channel, final Throwable e) {
    if (e == null) {
      LOGGER.debug("Connected to {}", channel.remoteAddress());
    } else {
      LOGGER.debug("Failed to connect to {}", channel.remoteAddress(), e);
    }
  }
}
