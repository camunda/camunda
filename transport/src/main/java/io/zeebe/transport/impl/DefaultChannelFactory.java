/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.transport.impl;

import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.transport.impl.TransportChannel.ChannelLifecycleListener;
import io.zeebe.transport.impl.TransportChannel.TransportChannelMetrics;
import io.zeebe.util.metrics.MetricsManager;
import java.nio.channels.SocketChannel;

public class DefaultChannelFactory implements TransportChannelFactory {
  private final TransportChannelMetrics metrics;

  public DefaultChannelFactory(MetricsManager metricsManager, String transportName) {
    this.metrics = new TransportChannel.TransportChannelMetrics(metricsManager, transportName);
  }

  public DefaultChannelFactory() {
    this(new MetricsManager(), "test");
  }

  @Override
  public TransportChannel buildClientChannel(
      ChannelLifecycleListener listener,
      RemoteAddressImpl remoteAddress,
      int maxMessageSize,
      FragmentHandler readHandler) {
    return new TransportChannel(listener, remoteAddress, maxMessageSize, readHandler, metrics);
  }

  @Override
  public TransportChannel buildServerChannel(
      ChannelLifecycleListener listener,
      RemoteAddressImpl remoteAddress,
      int maxMessageSize,
      FragmentHandler readHandler,
      SocketChannel media) {
    return new TransportChannel(
        listener, remoteAddress, maxMessageSize, readHandler, media, metrics);
  }
}
