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
package io.zeebe.gateway.impl.clustering;

import io.zeebe.gateway.api.commands.Topology;
import io.zeebe.gateway.api.commands.TopologyRequestStep1;
import io.zeebe.gateway.cmd.BrokerErrorException;
import io.zeebe.gateway.cmd.ClientCommandRejectedException;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.request.BrokerTopologyRequest;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

// TODO: remove with https://github.com/zeebe-io/zeebe/issues/1377
public class TopologyRequestImpl extends BrokerTopologyRequest implements TopologyRequestStep1 {

  private final BrokerClient client;

  public TopologyRequestImpl(BrokerClient client) {
    this.client = client;
  }

  @Override
  public ActorFuture<Topology> send() {
    final ActorFuture<Topology> future = new CompletableActorFuture<>();
    client.sendRequest(
        this,
        (response, error) -> {
          try {
            if (error == null) {
              if (response.isResponse()) {
                future.complete(new TopologyImpl(response.getResponse()));
              } else if (response.isRejection()) {
                final ClientCommandRejectedException exception =
                    new ClientCommandRejectedException(response.getRejection());
                future.completeExceptionally(exception);
              } else if (response.isError()) {
                final BrokerErrorException exception =
                    new BrokerErrorException(response.getError());
                future.completeExceptionally(exception);
              }
            } else {
              future.completeExceptionally(error);
            }
          } catch (Exception e) {
            future.completeExceptionally(e);
          }
        });
    return future;
  }
}
