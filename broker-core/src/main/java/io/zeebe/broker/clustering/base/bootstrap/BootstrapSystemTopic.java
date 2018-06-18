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
package io.zeebe.broker.clustering.base.bootstrap;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.*;
import static io.zeebe.broker.transport.TransportServiceNames.*;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames;
import io.zeebe.broker.clustering.base.partitions.PartitionInstallService;
import io.zeebe.broker.clustering.base.raft.RaftPersistentConfiguration;
import io.zeebe.broker.clustering.base.raft.RaftPersistentConfigurationManager;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.protocol.Protocol;
import io.zeebe.servicecontainer.*;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;

/**
 * Used to create the system topic (more precisely the single partition of the system topic). Checks
 * if the partition does already exist locally.
 *
 * <p>When operating as a standalone broker, this service is always installed on startup. When
 * operating as a multi-node cluster, this service is installed on the bootstrap node after the
 * expected count of nodes has joined the cluster.
 */
public class BootstrapSystemTopic extends Actor implements Service<Void> {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final Injector<RaftPersistentConfigurationManager>
      raftPersistentConfigurationManagerInjector = new Injector<>();

  private final int replicationFactor;
  private RaftPersistentConfigurationManager configurationManager;
  private ServiceStartContext serviceStartContext;

  private final BrokerCfg brokerCfg;

  public BootstrapSystemTopic(int replicationFactor, BrokerCfg brokerCfg) {
    this.replicationFactor = replicationFactor;
    this.brokerCfg = brokerCfg;
  }

  public Injector<RaftPersistentConfigurationManager>
      getRaftPersistentConfigurationManagerInjector() {
    return raftPersistentConfigurationManagerInjector;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    serviceStartContext = startContext;
    configurationManager = raftPersistentConfigurationManagerInjector.getValue();
    startContext.async(startContext.getScheduler().submitActor(this));
  }

  @Override
  protected void onActorStarted() {
    final ActorFuture<List<RaftPersistentConfiguration>> configurationsFuture =
        configurationManager.getConfigurations();
    actor.runOnCompletion(
        configurationsFuture,
        (configurations, throwable) -> {
          if (throwable == null) {
            final long count =
                configurations
                    .stream()
                    .filter(
                        configuration ->
                            configuration.getPartitionId() == Protocol.SYSTEM_PARTITION)
                    .count();

            if (count == 0) {
              installSystemPartition();
            } else {
              LOG.debug("Internal system partition already present. Not bootstrapping it.");
            }
          } else {
            throw new RuntimeException(throwable);
          }
        });
  }

  private void installSystemPartition() {
    final ActorFuture<RaftPersistentConfiguration> configurationFuture =
        configurationManager.createConfiguration(
            Protocol.SYSTEM_TOPIC_BUF,
            Protocol.SYSTEM_PARTITION,
            replicationFactor,
            Collections.emptyList());

    LOG.info(
        "Boostrapping internal system topic '{}' with replication factor {}.",
        Protocol.SYSTEM_TOPIC,
        replicationFactor);

    actor.runOnCompletion(
        configurationFuture,
        (configuration, throwable) -> {
          if (throwable != null) {
            throw new RuntimeException(throwable);
          } else {
            final String partitionName =
                String.format("%s-%d", Protocol.SYSTEM_TOPIC, Protocol.SYSTEM_PARTITION);
            final ServiceName<Void> partitionInstallServiceName =
                partitionInstallServiceName(partitionName);
            final PartitionInstallService partitionInstallService =
                new PartitionInstallService(brokerCfg, configuration, true);

            final ActorFuture<Void> partitionInstallFuture =
                serviceStartContext
                    .createService(partitionInstallServiceName, partitionInstallService)
                    .dependency(LOCAL_NODE, partitionInstallService.getLocalNodeInjector())
                    .dependency(
                        clientTransport(REPLICATION_API_CLIENT_NAME),
                        partitionInstallService.getClientTransportInjector())
                    .install();

            actor.runOnCompletion(
                partitionInstallFuture,
                (aVoid, installThrowable) -> {
                  if (installThrowable == null) {
                    final BootstrapSystemTopicReplication bootstrapSystemTopicReplication =
                        new BootstrapSystemTopicReplication();
                    serviceStartContext
                        .createService(
                            SYSTEM_PARTITION_BOOTSTRAP_REPLICATION_SERVICE_NAME,
                            bootstrapSystemTopicReplication)
                        .dependency(
                            ClusterBaseLayerServiceNames.leaderPartitionServiceName(partitionName),
                            bootstrapSystemTopicReplication.getPartitionInjector())
                        .install();

                    if (!brokerCfg.getTopics().isEmpty()) {
                      LOG.info("Bootstrapping default topics {}", brokerCfg.getTopics());
                      final BootstrapDefaultTopicsService bootstrapDefaultTopics =
                          new BootstrapDefaultTopicsService(brokerCfg.getTopics());
                      serviceStartContext
                          .createService(
                              DEFAULT_TOPICS_BOOTSTRAP_SERVICE_NAME, bootstrapDefaultTopics)
                          .dependency(
                              ClusterBaseLayerServiceNames.leaderPartitionServiceName(
                                  partitionName),
                              bootstrapDefaultTopics.getPartitionInjector())
                          .install();
                    }
                  } else {
                    configurationManager.deleteConfiguration(configuration);
                    throw new RuntimeException(installThrowable);
                  }
                });
          }
        });
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    stopContext.async(actor.close());
  }

  @Override
  public Void get() {
    return null;
  }
}
