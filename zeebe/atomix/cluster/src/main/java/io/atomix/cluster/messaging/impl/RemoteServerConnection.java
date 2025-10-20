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

import io.atomix.cluster.messaging.impl.ProtocolReply.Status;
import io.netty.channel.Channel;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Remote server connection manages messaging on the server side of a Netty connection. */
final class RemoteServerConnection extends AbstractServerConnection {
  private static final byte[] EMPTY_PAYLOAD = new byte[0];
  private static final Logger LOG = LoggerFactory.getLogger(RemoteServerConnection.class);

  private final Channel channel;

  RemoteServerConnection(final HandlerRegistry handlers, final Channel channel) {
    super(handlers);
    this.channel = channel;
  }

  @Override
  public void reply(final long messageId, final Status status, final Optional<byte[]> payload) {
    final ProtocolReply response =
        new ProtocolReply(messageId, payload.orElse(EMPTY_PAYLOAD), status);
    LOG.trace("Sending response {}", response);
    channel.writeAndFlush(response, channel.voidPromise());
  }
}
