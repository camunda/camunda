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
package io.zeebe.broker.it.util;

import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.client.impl.ControlMessageRequestHandler;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.impl.clustering.TopologyImpl;
import io.zeebe.client.impl.clustering.TopologyRequestImpl;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.agrona.DirectBuffer;

public class TopologyClient {

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ClientTransport transport;
  private final ControlMessageRequestHandler requestHandler;

  public TopologyClient(final ZeebeClientImpl zeebeClient) {
    transport = zeebeClient.getTransport();
    requestHandler =
        new ControlMessageRequestHandler(
            zeebeClient.getObjectMapper(), new TopologyRequestImpl(null, null));
  }

  public List<BrokerInfo> requestTopologyFromBroker(final SocketAddress socketAddress) {
    final RemoteAddress remoteAddress = transport.registerRemoteAndAwaitChannel(socketAddress);
    final ClientResponse response =
        transport
            .getOutput()
            .sendRequest(remoteAddress, requestHandler, Duration.ofSeconds(5))
            .join();
    final DirectBuffer responseBuffer = response.getResponseBuffer();

    messageHeaderDecoder.wrap(responseBuffer, 0);

    final int blockLength = messageHeaderDecoder.blockLength();
    final int version = messageHeaderDecoder.version();

    final int responseMessageOffset = messageHeaderDecoder.encodedLength();

    if (requestHandler.handlesResponse(messageHeaderDecoder)) {
      try {
        final TopologyImpl topology =
            (TopologyImpl)
                requestHandler.getResult(
                    responseBuffer, responseMessageOffset, blockLength, version);
        return topology.getBrokers();
      } catch (final Exception e) {
        // ignore
      }
    }

    return Collections.emptyList();
  }
}
