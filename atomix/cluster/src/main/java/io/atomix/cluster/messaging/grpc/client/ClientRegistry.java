/*
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
package io.atomix.cluster.messaging.grpc.client;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.messaging.grpc.TransportFactory;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.messaging.protocol.MessagingGrpc.MessagingStub;
import io.camunda.zeebe.util.CloseableSilently;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;

public final class ClientRegistry implements ClusterMembershipEventListener, CloseableSilently {
  private final ConcurrentMap<Address, Client> clients = new ConcurrentHashMap<>();
  private final TransportFactory transportFactory;

  public ClientRegistry(final TransportFactory transportFactory) {
    this.transportFactory = transportFactory;
  }

  public MessagingStub get(final Address address) {
    final var client = clients.computeIfAbsent(address, this::createClient);
    return client.getStub();
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    if (isUnreachable(event)) {
      shutdownClient(event.subject().address());
    }
  }

  @Override
  public void close() {
    final var toShutdown = new HashMap<>(clients);
    clients.clear();

    for (final var client : toShutdown.values()) {
      client.close();
    }
  }

  private boolean isUnreachable(final ClusterMembershipEvent event) {
    return event.type() == Type.MEMBER_REMOVED
        || (event.type() == Type.REACHABILITY_CHANGED && !event.subject().isReachable());
  }

  private void shutdownClient(final Address address) {
    final var client = clients.remove(address);
    CloseHelper.quietClose(client);
  }

  private Client createClient(final Address address) {
    final var channel = transportFactory.createClientBuilder(address.resolve()).build();
    final var stub =
        io.camunda.zeebe.messaging.protocol.MessagingGrpc.newStub(channel)
            .withDeadlineAfter(10, TimeUnit.SECONDS)
            .withCompression("gzip");

    return new Client(channel, stub);
  }
}
