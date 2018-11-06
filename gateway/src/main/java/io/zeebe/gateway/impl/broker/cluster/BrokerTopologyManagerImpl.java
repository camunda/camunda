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
package io.zeebe.gateway.impl.broker.cluster;

import io.zeebe.gateway.impl.broker.request.BrokerTopologyRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BrokerTopologyManagerImpl extends Actor implements BrokerTopologyManager {
  /** Interval in which the topology is refreshed even if the client is idle */
  public static final Duration MAX_REFRESH_INTERVAL_MILLIS = Duration.ofSeconds(10);

  /**
   * Shortest possible interval in which the topology is refreshed, even if the client is constantly
   * making new requests that require topology refresh
   */
  public static final Duration MIN_REFRESH_INTERVAL_MILLIS = Duration.ofMillis(300);

  protected final ClientOutput output;
  protected final BiConsumer<Integer, SocketAddress> registerEndpoint;

  protected final AtomicReference<BrokerClusterStateImpl> topology;
  protected final List<CompletableActorFuture<BrokerClusterState>> nextTopologyFutures =
      new ArrayList<>();

  protected final BrokerTopologyRequest topologyRequest = new BrokerTopologyRequest();

  protected int refreshAttempt = 0;
  protected long lastRefreshTime = -1;

  public BrokerTopologyManagerImpl(
      final ClientOutput output, final BiConsumer<Integer, SocketAddress> registerEndpoint) {
    this.output = output;
    this.registerEndpoint = registerEndpoint;

    this.topology = new AtomicReference<>(null);
  }

  @Override
  protected void onActorStarted() {
    actor.run(this::refreshTopology);
  }

  public ActorFuture<Void> close() {
    return actor.close();
  }

  /** @return the current known cluster state or null if the topology was not fetched yet */
  @Override
  public BrokerClusterState getTopology() {
    return topology.get();
  }

  @Override
  public ActorFuture<BrokerClusterState> requestTopology() {
    final CompletableActorFuture<BrokerClusterState> future = new CompletableActorFuture<>();

    actor.run(
        () -> {
          final boolean isFirstStagedRequest = nextTopologyFutures.isEmpty();
          nextTopologyFutures.add(future);

          if (isFirstStagedRequest) {
            scheduleNextRefresh();
          }
        });

    return future;
  }

  @Override
  public void withTopology(Consumer<BrokerClusterState> topologyConsumer) {
    final BrokerClusterStateImpl brokerClusterState = topology.get();
    if (brokerClusterState != null) {
      topologyConsumer.accept(brokerClusterState);
    } else {
      actor.run(
          () ->
              actor.runOnCompletion(
                  requestTopology(),
                  (topology, error) -> {
                    if (error == null) {
                      topologyConsumer.accept(topology);
                    } else {
                      withTopology(topologyConsumer);
                    }
                  }));
    }
  }

  private void scheduleNextRefresh() {
    final long now = ActorClock.currentTimeMillis();
    final long timeSinceLastRefresh = now - lastRefreshTime;

    if (timeSinceLastRefresh >= MIN_REFRESH_INTERVAL_MILLIS.toMillis()) {
      refreshTopology();
    } else {
      final long timeoutToNextRefresh =
          MIN_REFRESH_INTERVAL_MILLIS.toMillis() - timeSinceLastRefresh;
      actor.runDelayed(Duration.ofMillis(timeoutToNextRefresh), this::refreshTopology);
    }
  }

  @Override
  public void provideTopology(final TopologyResponseDto topology) {
    actor.call(
        () -> {
          // TODO: not sure we should complete the refresh futures in this case,
          //   as the response could be older than the time when the future was submitted
          onNewTopology(topology);
        });
  }

  private void refreshTopology() {
    final BrokerClusterStateImpl brokerClusterState = topology.get();
    final int endpoint;
    if (brokerClusterState != null) {
      endpoint = brokerClusterState.getRandomBroker();
    } else {
      // never fetched topology before so use initial contact point node
      endpoint = ClientTransport.UNKNOWN_NODE_ID;
    }
    final ActorFuture<ClientResponse> responseFuture =
        output.sendRequest(endpoint, topologyRequest, Duration.ofSeconds(1));

    refreshAttempt++;
    lastRefreshTime = ActorClock.currentTimeMillis();
    actor.runOnCompletion(responseFuture, this::handleResponse);
    actor.runDelayed(MAX_REFRESH_INTERVAL_MILLIS, scheduleIdleRefresh());
  }

  /** Only schedules topology refresh if there was no refresh attempt in the last ten seconds */
  private Runnable scheduleIdleRefresh() {
    final int currentAttempt = refreshAttempt;

    return () -> {
      // if no topology refresh attempt was made in the meantime
      if (currentAttempt == refreshAttempt) {
        actor.run(this::refreshTopology);
      }
    };
  }

  private void handleResponse(final ClientResponse clientResponse, final Throwable t) {
    if (t == null) {
      final BrokerResponse<TopologyResponseDto> response =
          topologyRequest.getResponse(clientResponse);
      if (response.isResponse()) {
        onNewTopology(response.getResponse());
      } else {
        failRefreshFutures(new RuntimeException("Failed to refresh topology: " + response));
      }
    } else {
      failRefreshFutures(t);
    }
  }

  private void onNewTopology(final TopologyResponseDto topology) {
    final BrokerClusterStateImpl newClusterState =
        new BrokerClusterStateImpl(topology, registerEndpoint);
    this.topology.set(newClusterState);
    completeRefreshFutures(newClusterState);
  }

  private void completeRefreshFutures(final BrokerClusterStateImpl newClusterState) {
    nextTopologyFutures.forEach(f -> f.complete(newClusterState));
    nextTopologyFutures.clear();
  }

  private void failRefreshFutures(final Throwable t) {
    nextTopologyFutures.forEach(f -> f.completeExceptionally("Could not refresh topology", t));
    nextTopologyFutures.clear();
  }
}
