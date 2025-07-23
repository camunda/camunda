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

import static io.atomix.cluster.messaging.impl.MessagingMetrics.CHANNEL_ID_ATTRIBUTE;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.util.collection.Tuple;
import io.netty.channel.Channel;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Internal Netty channel pool. */
class ChannelPool {
  private static final Logger LOGGER = LoggerFactory.getLogger(ChannelPool.class);

  private final Function<Address, CompletableFuture<Channel>> factory;
  private final int size;
  private final Map<Tuple<Address, InetAddress>, ChannelsForAddress> channels =
      Maps.newConcurrentMap();

  ChannelPool(final Function<Address, CompletableFuture<Channel>> factory, final int size) {
    this.factory = factory;
    this.size = size;
  }

  /**
   * Returns the channel pool for the given address.
   *
   * @param address the address for which to return the channel pool
   * @return the channel pool for the given address
   */
  private ChannelsForAddress getChannelPool(final Address address, final InetAddress inetAddress) {
    final Tuple<Address, InetAddress> channelPoolIdentifier = new Tuple<>(address, inetAddress);

    final ChannelsForAddress channelPool = channels.get(channelPoolIdentifier);
    if (channelPool != null) {
      return channelPool;
    }
    return channels.computeIfAbsent(
        channelPoolIdentifier,
        e -> {
          final List<CompletableFuture<Channel>> defaultList = new ArrayList<>(size);
          for (int i = 0; i < size; i++) {
            defaultList.add(null);
          }
          return new ChannelsForAddress(
              Lists.newCopyOnWriteArrayList(defaultList), Maps.newConcurrentMap());
        });
  }

  /**
   * Returns the channel offset for the given message type.
   *
   * @param messageType the message type for which to return the channel offset
   * @return the channel offset for the given message type
   */
  private int getChannelOffset(final String messageType) {
    return Math.abs(messageType.hashCode() % size);
  }

  private CompletableFuture<Channel> lookupChannel(
      final ChannelsForAddress channelsForAddress,
      final String messageType,
      final boolean dedicatedChannel) {
    if (dedicatedChannel) {
      return channelsForAddress.dedicatedChannels.get(messageType);
    }
    final var offset = getChannelOffset(messageType);
    return channelsForAddress.channels.get(offset);
  }

  private void setChannel(
      final ChannelsForAddress channelsForAddress,
      final String messageType,
      final boolean dedicatedChannel,
      final CompletableFuture<Channel> channelFuture) {
    if (dedicatedChannel) {
      channelsForAddress.dedicatedChannels.put(messageType, channelFuture);
      channelFuture.whenComplete(
          (channel, error) -> {
            if (error == null) {
              channel.attr(CHANNEL_ID_ATTRIBUTE).set("dedicated-" + messageType);
            }
          });
    } else {
      final var offset = getChannelOffset(messageType);
      channelsForAddress.channels.set(offset, channelFuture);
      channelFuture.whenComplete(
          (channel, error) -> {
            if (error == null) {
              channel.attr(CHANNEL_ID_ATTRIBUTE).set("shared-" + offset);
            }
          });
    }
  }

  /**
   * Gets or creates a pooled channel to the given address for the given message type.
   *
   * @param address the address for which to get the channel
   * @param messageType the message type for which to get the channel
   * @return a future to be completed with a channel from the pool
   */
  CompletableFuture<Channel> getChannel(
      final Address address, final String messageType, final boolean dedicatedChannel) {
    final InetAddress inetAddress = address.getAddress();

    final ChannelsForAddress channelPool = getChannelPool(address, inetAddress);

    CompletableFuture<Channel> channelFuture =
        lookupChannel(channelPool, messageType, dedicatedChannel);
    if (channelFuture == null || channelFuture.isCompletedExceptionally()) {
      synchronized (channelPool) {
        channelFuture = lookupChannel(channelPool, messageType, dedicatedChannel);
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
                              removeChannel(
                                  channelPool, messageType, dedicatedChannel, finalFuture);
                            }
                          });
                } else {
                  LOGGER.debug("Failed to connect to {}", address, error);
                }
              });
          setChannel(channelPool, messageType, dedicatedChannel, channelFuture);
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
                currentFuture = lookupChannel(channelPool, messageType, dedicatedChannel);
                if (currentFuture == finalFuture) {
                  setChannel(channelPool, messageType, dedicatedChannel, null);
                } else if (currentFuture == null) {
                  currentFuture = factory.apply(address);
                  currentFuture.whenComplete(this::logConnection);
                  setChannel(channelPool, messageType, dedicatedChannel, currentFuture);
                }
              }

              if (currentFuture == finalFuture) {
                getChannel(address, messageType, dedicatedChannel)
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

  private void removeChannel(
      final ChannelsForAddress channelPool,
      final String messageType,
      final boolean dedicatedChannel,
      final CompletableFuture<Channel> finalFuture) {
    final var currentFuture = lookupChannel(channelPool, messageType, dedicatedChannel);
    // check if new channel is already replaced before removing it.
    if (finalFuture == currentFuture) {
      setChannel(channelPool, messageType, dedicatedChannel, null);
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

  record ChannelsForAddress(
      List<CompletableFuture<Channel>> channels,
      Map<String, CompletableFuture<Channel>> dedicatedChannels) {}
}
