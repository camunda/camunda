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
package io.zeebe.broker.clustering.api;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.raft.RaftPersistentConfigurationManager;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ServerInputSubscription;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ManagementApiRequestHandlerService extends Actor implements Service<Void> {
  private final Injector<BufferingServerTransport> serverTransportInjector = new Injector<>();
  private final Injector<RaftPersistentConfigurationManager>
      raftPersistentConfigurationManagerInjector = new Injector<>();

  private final BrokerCfg brokerCfg;
  private BufferingServerTransport serverTransport;
  private ManagementApiRequestHandler managementApiRequestHandler;
  private RaftPersistentConfigurationManager raftPersistentConfigurationManager;

  private final Map<Integer, Partition> trackedSnapshotPartitions = new ConcurrentHashMap<>();
  private ServiceGroupReference<Partition> leaderPartitionsGroupReference =
      ServiceGroupReference.<Partition>create()
          .onAdd(
              (name, partition) ->
                  trackedSnapshotPartitions.put(partition.getInfo().getPartitionId(), partition))
          .onRemove(
              (name, partition) ->
                  trackedSnapshotPartitions.remove(partition.getInfo().getPartitionId()))
          .build();

  public ManagementApiRequestHandlerService(BrokerCfg brokerCfg) {
    this.brokerCfg = brokerCfg;
  }

  @Override
  public String getName() {
    return "management-api";
  }

  @Override
  public void start(ServiceStartContext startContext) {
    serverTransport = serverTransportInjector.getValue();
    raftPersistentConfigurationManager = raftPersistentConfigurationManagerInjector.getValue();
    managementApiRequestHandler =
        new ManagementApiRequestHandler(
            raftPersistentConfigurationManager,
            actor,
            startContext,
            brokerCfg,
            trackedSnapshotPartitions);

    startContext.async(startContext.getScheduler().submitActor(this, true));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(actor.close());
  }

  @Override
  protected void onActorStarting() {
    final ActorFuture<ServerInputSubscription> subscriptionFuture =
        serverTransport.openSubscription(
            "clusterManagement", managementApiRequestHandler, managementApiRequestHandler);

    actor.runOnCompletion(
        subscriptionFuture,
        (subscription, throwable) -> {
          if (throwable != null) {
            throw new RuntimeException(throwable);
          } else {
            actor.consume(
                subscription,
                () -> {
                  if (subscription.poll() == 0) {
                    actor.yield();
                  }
                });
          }
        });
  }

  @Override
  public Void get() {
    return null;
  }

  public Injector<BufferingServerTransport> getServerTransportInjector() {
    return serverTransportInjector;
  }

  public Injector<RaftPersistentConfigurationManager>
      getRaftPersistentConfigurationManagerInjector() {
    return raftPersistentConfigurationManagerInjector;
  }

  public ServiceGroupReference<Partition> getLeaderPartitionsGroupReference() {
    return leaderPartitionsGroupReference;
  }
}
