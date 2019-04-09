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
package io.zeebe.broker.system;

import io.zeebe.broker.Broker;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.broker.system.configuration.SocketBindingCfg;
import io.zeebe.broker.system.configuration.ThreadsCfg;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.impl.ServiceContainerImpl;
import io.zeebe.util.TomlConfigurationReader;
import io.zeebe.util.metrics.MetricsManager;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;

public class SystemContext implements AutoCloseable {
  public static final Logger LOG = Loggers.SYSTEM_LOGGER;
  public static final String BROKER_ID_LOG_PROPERTY = "broker-id";
  public static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(20);
  public static final String NODE_ID_ERROR_MSG =
      "Node id %s needs to be non negative and smaller then cluster size %s.";
  public static final String REPLICATION_FACTOR_ERROR_MSG =
      "Replication factor %s needs to be larger then zero and not larger then cluster size %s.";

  protected ServiceContainer serviceContainer;

  protected final List<Component> components = new ArrayList<>();

  protected final BrokerCfg brokerCfg;

  protected final List<ActorFuture<?>> requiredStartActions = new ArrayList<>();
  private final List<Closeable> closeablesToReleaseResources = new ArrayList<>();

  protected Map<String, String> diagnosticContext;
  protected ActorScheduler scheduler;

  private MetricsManager metricsManager;
  private Duration closeTimeout;

  public SystemContext(String configFileLocation, final String basePath, final ActorClock clock) {
    if (!Paths.get(configFileLocation).isAbsolute()) {
      configFileLocation =
          Paths.get(basePath, configFileLocation).normalize().toAbsolutePath().toString();
    }

    brokerCfg = TomlConfigurationReader.read(configFileLocation, BrokerCfg.class);

    initSystemContext(clock, basePath);
  }

  public SystemContext(
      final InputStream configStream, final String basePath, final ActorClock clock) {
    brokerCfg = TomlConfigurationReader.read(configStream, BrokerCfg.class);

    initSystemContext(clock, basePath);
  }

  public SystemContext(final BrokerCfg brokerCfg, final String basePath, final ActorClock clock) {
    this.brokerCfg = brokerCfg;

    initSystemContext(clock, basePath);
  }

  private void initSystemContext(final ActorClock clock, final String basePath) {
    LOG.debug("Initializing configuration with base path {}", basePath);

    brokerCfg.init(basePath);
    validateConfiguration();

    final SocketBindingCfg clientApiCfg = brokerCfg.getNetwork().getClient();
    final String brokerId = String.format("%s:%d", clientApiCfg.getHost(), clientApiCfg.getPort());

    this.diagnosticContext = Collections.singletonMap(BROKER_ID_LOG_PROPERTY, brokerId);

    // TODO: submit diagnosticContext to actor scheduler once supported
    this.metricsManager = initMetricsManager(brokerId);
    this.scheduler = initScheduler(clock, brokerId);
    this.serviceContainer = new ServiceContainerImpl(this.scheduler);
    this.scheduler.start();

    initBrokerInfoMetric();

    setCloseTimeout(CLOSE_TIMEOUT);
  }

  private void validateConfiguration() {
    final ClusterCfg cluster = brokerCfg.getCluster();

    final int partitionCount = cluster.getPartitionsCount();
    if (partitionCount < 1) {
      throw new IllegalArgumentException("Partition count must not be smaller then 1.");
    }

    final int clusterSize = cluster.getClusterSize();
    final int nodeId = cluster.getNodeId();
    if (nodeId < 0 || nodeId >= clusterSize) {
      throw new IllegalArgumentException(String.format(NODE_ID_ERROR_MSG, nodeId, clusterSize));
    }

    final int replicationFactor = cluster.getReplicationFactor();
    if (replicationFactor < 1 || replicationFactor > clusterSize) {
      throw new IllegalArgumentException(
          String.format(REPLICATION_FACTOR_ERROR_MSG, replicationFactor, clusterSize));
    }
  }

  private MetricsManager initMetricsManager(final String brokerId) {
    final Map<String, String> globalLabels = new HashMap<>();
    globalLabels.put("cluster", "zeebe");
    globalLabels.put("node", brokerId);
    return new MetricsManager("zb_", globalLabels);
  }

  private void initBrokerInfoMetric() {
    // one-shot metric to submit metadata
    metricsManager
        .newMetric("broker_info")
        .type("counter")
        .label("version", Broker.VERSION)
        .create()
        .incrementOrdered();
  }

  private ActorScheduler initScheduler(final ActorClock clock, final String brokerId) {
    final ThreadsCfg cfg = brokerCfg.getThreads();

    final int cpuThreads = cfg.getCpuThreadCount();
    final int ioThreads = cfg.getIoThreadCount();

    Loggers.SYSTEM_LOGGER.info(
        "Scheduler configuration: Threads{cpu-bound: {}, io-bound: {}}.", cpuThreads, ioThreads);

    return ActorScheduler.newActorScheduler()
        .setActorClock(clock)
        .setMetricsManager(metricsManager)
        .setCpuBoundActorThreadCount(cpuThreads)
        .setIoBoundActorThreadCount(ioThreads)
        .setSchedulerName(brokerId)
        .build();
  }

  public ActorScheduler getScheduler() {
    return scheduler;
  }

  public ServiceContainer getServiceContainer() {
    return serviceContainer;
  }

  public void addComponent(final Component component) {
    this.components.add(component);
  }

  public List<Component> getComponents() {
    return components;
  }

  public void init() {
    serviceContainer.start();

    for (final Component brokerComponent : components) {
      try {
        brokerComponent.init(this);
      } catch (final RuntimeException e) {
        close();
        throw e;
      }
    }

    try {
      for (final ActorFuture<?> requiredStartAction : requiredStartActions) {
        requiredStartAction.get(40, TimeUnit.SECONDS);
      }
    } catch (final Exception e) {
      LOG.error("Could not start broker", e);
      close();
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    LOG.info("Closing...");

    try {
      serviceContainer.close(getCloseTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (final TimeoutException e) {
      LOG.error("Failed to close broker within {} seconds.", CLOSE_TIMEOUT, e);
    } catch (final ExecutionException | InterruptedException e) {
      LOG.error("Exception while closing broker", e);
    } finally {

      for (final Closeable delegate : closeablesToReleaseResources) {
        try {
          delegate.close();
        } catch (final IOException ioe) {
          LOG.error("Exception while releasing resources", ioe);
        }
      }

      try {
        scheduler.stop().get(getCloseTimeout().toMillis(), TimeUnit.MILLISECONDS);
      } catch (final TimeoutException e) {
        LOG.error("Failed to close scheduler within {} seconds", CLOSE_TIMEOUT, e);
      } catch (final ExecutionException | InterruptedException e) {
        LOG.error("Exception while closing scheduler", e);
      }
    }
  }

  public BrokerCfg getBrokerConfiguration() {
    return brokerCfg;
  }

  public void addRequiredStartAction(final ActorFuture<?> future) {
    requiredStartActions.add(future);
  }

  public void addResourceReleasingDelegate(final Closeable delegate) {
    closeablesToReleaseResources.add(delegate);
  }

  public Map<String, String> getDiagnosticContext() {
    return diagnosticContext;
  }

  public Duration getCloseTimeout() {
    return closeTimeout;
  }

  public void setCloseTimeout(final Duration closeTimeout) {
    this.closeTimeout = closeTimeout;
    scheduler.setBlockingTasksShutdownTime(closeTimeout);
  }
}
