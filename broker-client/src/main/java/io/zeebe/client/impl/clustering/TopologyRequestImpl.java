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
package io.zeebe.client.impl.clustering;

import io.zeebe.client.api.commands.Topology;
import io.zeebe.client.api.commands.TopologyRequestStep1;
import io.zeebe.client.impl.ControlMessageRequest;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.protocol.clientapi.ControlMessageType;
import java.util.HashMap;

public class TopologyRequestImpl extends ControlMessageRequest<Topology>
    implements TopologyRequestStep1 {
  private static final Object EMPTY_REQUEST = new HashMap<>();

  private final ClientTopologyManager topologyManager;

  public TopologyRequestImpl(RequestManager commandManager, ClientTopologyManager topologyManager) {
    super(commandManager, ControlMessageType.REQUEST_TOPOLOGY, TopologyImpl.class);
    this.topologyManager = topologyManager;
  }

  @Override
  public void onResponse(Topology response) {
    if (topologyManager != null) {
      topologyManager.provideTopology(response);
    }
  }

  @Override
  public Object getRequest() {
    return EMPTY_REQUEST;
  }
}
