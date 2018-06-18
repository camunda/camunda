/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.orchestration;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.topology.*;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;

public class NodeSelector extends Actor
    implements Service<NodeSelector>, TopologyMemberListener, TopologyPartitionListener {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;
  public static final Duration NODE_PENDING_TIMEOUT = Duration.ofSeconds(30);

  private final Injector<TopologyManager> topologyManagerInjector = new Injector<>();

  private final List<NodeLoad> loads = new ArrayList<>();

  @Override
  public String getName() {
    return "node-selector";
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    final TopologyManager topologyManager = topologyManagerInjector.getValue();
    topologyManager.addTopologyMemberListener(this);
    topologyManager.addTopologyPartitionListener(this);
    startContext.async(startContext.getScheduler().submitActor(this));
  }

  @Override
  public NodeSelector get() {
    return this;
  }

  @Override
  public void onMemberAdded(final NodeInfo memberInfo, final Topology topology) {
    actor.run(
        () -> {
          LOG.debug("Add node {} to current state.", memberInfo);
          loads.add(new NodeLoad(memberInfo));
          loads.sort(this::loadComparator);
        });
  }

  @Override
  public void onMemberRemoved(final NodeInfo memberInfo, final Topology topology) {
    actor.run(
        () -> {
          LOG.debug("Remove node {} from current state.", memberInfo);
          loads.remove(new NodeLoad(memberInfo));
          loads.sort(this::loadComparator);
        });
  }

  @Override
  public void onPartitionUpdated(final PartitionInfo partitionInfo, final NodeInfo member) {
    actor.run(
        () -> {
          final Optional<NodeLoad> nodeOptional =
              loads.stream().filter(node -> node.getNodeInfo().equals(member)).findFirst();

          if (nodeOptional.isPresent()) {
            final NodeLoad nodeLoad = nodeOptional.get();
            final boolean added = nodeLoad.addPartition(partitionInfo);
            if (added) {
              nodeLoad.removePending(partitionInfo);
              loads.sort(this::loadComparator);
              LOG.debug("Increased load of node {} by partition {}", member, partitionInfo);
            }
          } else {
            LOG.debug("Node {} was not found in current state.", member);
          }
        });
  }

  public ActorFuture<NodeInfo> getNextSocketAddress(final PartitionInfo forPartitionInfo) {
    final CompletableActorFuture<NodeInfo> nextAddressFuture = new CompletableActorFuture<>();
    actor.run(
        () -> {
          final Optional<NodeLoad> nextOptional =
              loads
                  .stream()
                  .filter(nodeLoad -> nodeLoad.doesNotHave(forPartitionInfo))
                  .min(this::loadComparator);

          if (nextOptional.isPresent()) {
            final NodeLoad nextNode = nextOptional.get();
            actor.runDelayed(NODE_PENDING_TIMEOUT, () -> nextNode.removePending(forPartitionInfo));
            nextNode.addPendingPartiton(forPartitionInfo);
            loads.sort(this::loadComparator);
            nextAddressFuture.complete(nextNode.getNodeInfo());
          } else {
            final String errorMessage =
                String.format("Found no next address, from current state %s", loads);
            nextAddressFuture.completeExceptionally(new IllegalStateException(errorMessage));
          }
        });
    return nextAddressFuture;
  }

  public Injector<TopologyManager> getTopologyManagerInjector() {
    return topologyManagerInjector;
  }

  private int loadComparator(final NodeLoad load1, final NodeLoad load2) {
    final int nodeLoad1 = load1.getLoad().size() + load1.getPendings().size();
    final int nodeLoad2 = load2.getLoad().size() + load2.getPendings().size();
    return Integer.compare(nodeLoad1, nodeLoad2);
  }
}
