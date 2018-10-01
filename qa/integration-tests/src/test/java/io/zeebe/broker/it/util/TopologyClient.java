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

import io.zeebe.gateway.api.commands.BrokerInfo;
import io.zeebe.gateway.impl.broker.request.BrokerTopologyRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.gateway.impl.clustering.TopologyImpl;
import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.SocketAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class TopologyClient {

  private final ClientTransport transport;
  private final BrokerTopologyRequest topologyRequest = new BrokerTopologyRequest();

  public TopologyClient(final ClientTransport transport) {
    this.transport = transport;
  }

  public List<BrokerInfo> requestTopologyFromBroker(
      final int nodeId, final SocketAddress socketAddress) {
    transport.registerEndpointAndAwaitChannel(nodeId, socketAddress);
    final ClientResponse clientResponse =
        transport
            .getOutput()
            .sendRequestWithRetry(() -> nodeId, b -> false, topologyRequest, Duration.ofSeconds(5))
            .join();

    final BrokerResponse<TopologyResponseDto> response =
        topologyRequest.getResponse(clientResponse);

    if (response.isResponse()) {
      final TopologyImpl topology = new TopologyImpl(response.getResponse());
      return topology.getBrokers();
    } else {
      return Collections.emptyList();
    }
  }
}
